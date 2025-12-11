package org.acme.foodpackaging.bootstrap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.apache.commons.math3.util.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.*;
import static org.acme.foodpackaging.sql.SqlQueries.*;
import static org.acme.foodpackaging.sql.SqlQueries.UPDATE_PDAYDTF;

@ApplicationScoped
public class LoadData {

    private static final Logger log = LoggerFactory.getLogger(LoadData.class);

    @Inject
    PackagingScheduleRepository repository;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    private ConcurrentMap<String, String> linesIdWithNamesMap;

    private Map<String, Product> allProductsMap;

    private CleaningCalculator calculator;

    Map<String, Map<String, Integer>> lineSpeeds;

    @PostConstruct
    private void init() {
        this.linesIdWithNamesMap = loadLinesIdWithNames();
        this.allProductsMap = loadProductfromDB();
        this.calculator = new CleaningCalculator();
        this.lineSpeeds = loadSpeedsFromDB();
    }

    public PackagingSchedule loadDataByDate(LocalDate START_DATE, LocalDate END_DATE,
                                            LocalDateTime idealEndDateTime,
                                            LocalDateTime maxEndDateTime,
                                            Map<String, LocalDateTime> lineStartsTime) {

        LocalDateTime minStartDateTime = Collections.min(lineStartsTime.values());

        return initSolution(START_DATE, END_DATE, lineStartsTime,
                minStartDateTime, idealEndDateTime, maxEndDateTime);
    }

    @Transactional
    public PackagingSchedule initSolution(LocalDate START_DATE, LocalDate END_DATE,
                                          Map<String, LocalDateTime> lineStartsTime,
                                          LocalDateTime minStartDateTime,
                                          LocalDateTime idealEndDateTime,
                                          LocalDateTime maxEndDateTime) {
        PackagingSchedule solution = new PackagingSchedule();
        solution.setWorkCalendar(new WorkCalendar(START_DATE, END_DATE, minStartDateTime, idealEndDateTime, maxEndDateTime));

        List<Line> lines = createLines(lineStartsTime.size(), lineStartsTime);
        Set<Product> productSet = new HashSet<>();

        List<Job> jobs = loadJobs(START_DATE, minStartDateTime, idealEndDateTime,
                maxEndDateTime, allProductsMap, productSet);

        List<Product> products = new ArrayList<>(productSet);
        calculator.cleaningCalculate(products);

        solution.setLines(lines);
        solution.setProducts(products);
        solution.setJobs(jobs);

        return solution;
    }

    public PackagingSchedule loadNextDay(PackagingSchedule solution) {

        refreshJobsNextDay(solution);
        return solution;

    }

    public record DbJobInfo(
            int snpz,
            int np,
            int quantity,
            int priority,
            double mass,
            String shortName,
            String kmc,
            LocalDateTime dti,
            LocalDateTime dtf
    ) {}

    private Map<Integer, DbJobInfo> loadDbJobInfo(LocalDate planningDay) {

        LocalDateTime planningDayStart = planningDay.atStartOfDay();
        LocalDateTime nextDayStart = planningDay.plusDays(1).atStartOfDay();

        Map<Integer, DbJobInfo> map = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(LOAD_DLC_JOBS)) {

            ps.setObject(1, planningDayStart);
            ps.setObject(2, nextDayStart);
            ps.setString(3, "0119030000");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    BigDecimal npVal = rs.getBigDecimal("NP");
                    if (npVal == null || npVal.intValue() == 0) continue;

                    int np = npVal.intValue();

                    DbJobInfo info = new DbJobInfo(
                            rs.getInt("SNPZ"), np,
                            rs.getInt("KOLEV"),
                            rs.getInt("UX"),
                            rs.getDouble("MASSA"),
                            rs.getString("SNM"),
                            rs.getString("KMC"),
                            rs.getTimestamp("DTI").toLocalDateTime(),
                            rs.getTimestamp("DTF").toLocalDateTime()
                    );

                    map.put(info.snpz(), info);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed loading DbJobInfo", e);
        }

        return map;
    }

    public void refreshJobsNextDay(PackagingSchedule schedule) {

        LocalDate planningDay = schedule.getWorkCalendar().getFromDate();
        Map<Integer, DbJobInfo> dbJobsNextDay = loadDbJobInfo(planningDay);

        List<Job> currentJobs = schedule.getJobs();

        Map<Integer, Job> scheduleMap = currentJobs.stream()
                .collect(Collectors.toMap(Job::getSnpz, j -> j));

        List<Job> toAdd = new ArrayList<>();
        List<Job> toRemove = new ArrayList<>();

        boolean newProductsAppeared = false;

        for (DbJobInfo info : dbJobsNextDay.values()) {

            if (!scheduleMap.containsKey(info.snpz())) {

                Product product = schedule.getProducts().stream()
                        .filter(p -> p.getId().equals(info.kmc()))
                        .findFirst()
                        .orElse(null);

                if (product == null) {
                    product = allProductsMap.get(info.kmc());
                    if (product == null) {
                        throw new IllegalStateException("Unknown product KMC=" + info.kmc());
                    }
                    schedule.getProducts().add(product);
                    newProductsAppeared = true;
                }

                Job newJob = createJob(
                        String.valueOf(info.snpz()),
                        info.shortName,
                        info.snpz(), info.np(), product,
                        info.mass(), info.quantity(), info.priority(),
                        schedule.getWorkCalendar().getMinStartDateTime(),
                        schedule.getWorkCalendar().getIdealEndDateTime(),
                        schedule.getWorkCalendar().getMaxEndDateTime()
                );

                newJob.setLineSpeeds(lineSpeeds);
                toAdd.add(newJob);
            }
        }

        for (Job job : currentJobs) {
            if (!dbJobsNextDay.containsKey(job.getSnpz()) && !job.isMaintenance()) {
                toRemove.add(job);
            }
        }

        for (Job jobToRemove : toRemove) {

            schedule.getJobs().remove(jobToRemove);

            for (Line line : schedule.getLines()) {

                List<Job> lineJobs = line.getJobs();

                int index = lineJobs.indexOf(jobToRemove);
                if (index >= 0) {
                    lineJobs.remove(index);

                    fixLineJobs(line);
                }
            }
        }

        pinnAllLines(schedule.getLines());
        schedule.getJobs().addAll(toAdd);

        if (newProductsAppeared) {
            calculator.cleaningCalculate(schedule.getProducts());
        }
    }

    private Map<String, Map<String, Integer>> loadSpeedsFromDB() {
        Map<Pair<String, String>, Integer> mapSpeed = getSpeedsfromDB();
        Map<String, Map<String, Integer>> finalMap = new HashMap<>();

        Set<String> allTypes = new HashSet<>();
        for (Pair<String, String> key : mapSpeed.keySet()) {
            allTypes.add(key.getSecond());
        }

        for (Map.Entry<Pair<String, String>, Integer> entry : mapSpeed.entrySet()) {
            Pair<String, String> key = entry.getKey();
            String line = key.getFirst();

            String type = key.getSecond();
            Integer speed = entry.getValue();

            finalMap.computeIfAbsent(line, l -> {
                Map<String, Integer> m = new HashMap<>();
                for (String t : allTypes) {
                    m.put(t, 0);
                }
                return m;
            });

            finalMap.get(line).put(type, speed);
        }
        for (Map<String, Integer> typeMap : finalMap.values()) {
            for (String type : allTypes) {
                typeMap.putIfAbsent(type, 0);
            }
        }

        return finalMap;
    }

    private List<CleaningRule> loadCleaningRulesfromDB() {

        List<CleaningRule> rules = new ArrayList<>();

        ResultSet resultSet = null;
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();) {
            resultSet = statement.executeQuery(LOAD_CLEANING_RULES);

            while (resultSet.next()) {
                String parameter = resultSet.getString("NPAR");
                String from_value = resultSet.getString("FROM_VALUE");
                String to_value = resultSet.getString("TO_VALUE");
                int duration = resultSet.getInt("DUR");
                CleaningRule rule = new CleaningRule(parameter, from_value, to_value, duration);
                rules.add(rule);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load cleaning rules from DB", e);
        }
        return rules;
    }

    private Map<Pair<String, String>, Integer> getSpeedsfromDB() {
        Map<Pair<String, String>, Integer> lines = new HashMap<>();

        ResultSet resultSet = null;
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();) {
            resultSet = statement.executeQuery(LOAD_LINES_SPEEDS);

            while (resultSet.next()) {
                String krc = resultSet.getString("KRC");
                String grf = resultSet.getString("GRF");
                String prod = resultSet.getString("PROD");
                Pair<String, String> typeLine = new Pair<>(krc, grf);
                lines.put(typeLine, Integer.valueOf(prod));

            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load lines from DB", e);
        }
        return lines;
    }

    private Map<String, Product> loadProductfromDB() {
        Map<String, Product> productsMap = new HashMap<>();
        ResultSet resultSet = null;
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();) {
            resultSet = statement.executeQuery(LOAD_PRODUCTS);

            while (resultSet.next()) {
                String kmc = resultSet.getString("KMC");
                String krKmc = resultSet.getString("KRKMC");
                String shortName = resultSet.getString("SNM");
                String ean13 = resultSet.getString("EAN13");
                String type = resultSet.getString("GRF");
                String glaze = resultSet.getString("TGLAZ");
                String curdMass = resultSet.getString("TMASS");
                String filling = resultSet.getString("TFBF");
                productsMap.put(kmc, new Product(shortName, kmc, krKmc, ean13, type, glaze, curdMass, filling));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load products from DB", e);
        }
        return productsMap;
    }

    // Обновляем loadJobs чтобы принимать параметры
    private List<Job> loadJobs(LocalDate date, LocalDateTime minStartDateTime,
                               LocalDateTime idealEndDateTime, LocalDateTime maxEndDateTime,
                               Map<String, Product> allProductsMap, Set<Product> productsSet) {
        List<Job> jobs = new ArrayList<>();

        LocalDateTime dt = date.atStartOfDay();

        try (Connection connection = DriverManager.getConnection(dbUrl);
             PreparedStatement preparedStatement = connection.prepareStatement(LOAD_JOBS_FOR_SELECTED_DATE)) {
            preparedStatement.setObject(1, dt);    // Параметр для v.DTI
            preparedStatement.setString(2, "0119030000");          // Параметр для v.KSK
            preparedStatement.setDouble(3, 0.1);                  // Параметр для m.MASSA

            int job_id = 0;
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    BigDecimal npVal = resultSet.getBigDecimal("NP");
                    if(npVal == null || npVal.intValue() == 0) continue;
                    int np = npVal.intValue();
                    int quantity = resultSet.getInt("KOLEV");          // количество
                    int priority = resultSet.getObject("UX") != null ? resultSet.getInt("UX") : 0;// Приоритет выполнения
                    int snpz = resultSet.getInt("SNPZ");
                    double mass = resultSet.getDouble("MASSA");     // масса партии
                    String ean13 = resultSet.getString("EAN13");   // Идентификатор продукта EAN13
                    String kmc = resultSet.getString("KMC");      //  Идентификатор продукта ERP
                    String name = resultSet.getString("NAME");   // Название
                    String shortName = resultSet.getString(("SNM"));        // Сокращенное название
                    // Список со всем возможным ассортиментов продуктов
                    Product product = allProductsMap.get(kmc);
                    if (product == null) {
                        throw new IllegalStateException("KMC=" + kmc + " не найден в таблице продукции для планировщика");
                    }
                    productsSet.add(product); // Set для инициализации списк апродукта
                    // Создание партий
                    Job job = createJob(
                            String.valueOf(++job_id), cleanSyrkiName(shortName), snpz, np, product, mass, quantity, priority,
                            minStartDateTime, idealEndDateTime, maxEndDateTime  // Используем параметры
                    );
                    jobs.add(job);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load jobs from DB", e);
        }
        Product maintenanceProduct = new Product(  // фиктивный продукт для сервисной работы
                "Maintenance Product",
                "MAINTENANCE", "", "", "", "", "", ""
        );
        productsSet.add(maintenanceProduct);

        for (Job job : jobs) {
            job.setLineSpeeds(lineSpeeds);
        }

        return jobs;
    }

    private List<Line> createLines(int lineCount, Map<String, LocalDateTime> lineStartsTime) {
        List<Line> lines = new ArrayList<>(lineCount);
        for (Map.Entry<String, LocalDateTime> entry : lineStartsTime.entrySet()) {
            String lineName = linesIdWithNamesMap.get(entry.getKey());
            Line line = new Line(entry.getKey(), lineName, entry.getValue());
            lines.add(line);
        }
        return lines;
    }

    private ConcurrentMap<String, String> loadLinesIdWithNames() {
        ConcurrentMap<String, String> linesNamesById = new ConcurrentHashMap<>();
        ResultSet resultSet = null;
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();) {
            resultSet = statement.executeQuery(LOAD_LINES_WITH_NAME);

            while (resultSet.next()) {
                String lineId = resultSet.getString("KRC");
                String lineName = resultSet.getString("SNM");
                linesNamesById.put(lineId, lineName);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load lines id with names from DB", e);
        }
        return linesNamesById;
    }

    public Map<String, String> getLinesIdWithNamesMap() {
        return linesIdWithNamesMap;
    }

    private Job createJob(String id, String jobName, int snpz, int np, Product product, double mass, int quantity, int priority,
                          LocalDateTime minStartDateTime, LocalDateTime idealEndDateTime, LocalDateTime maxEndDateTime) {
        return new Job(id, jobName, snpz, np, product, mass, quantity,
                minStartDateTime,
                idealEndDateTime,
                maxEndDateTime, priority, false
        );
    }

    private static String cleanSyrkiName(String input) {
        return input.replaceFirst(
                "(?i)Сырок\\s*(тв\\.\\s*г\\.с|тв\\.\\s*гл\\.с|тв\\.\\s*гл\\.|тв\\.\\s*г\\.|гл\\.|тв\\.\\s*глазированный|глазированный|тв\\.\\s*глазир\\.)",
                ""
        ).trim();
    }

    public Map<String, Map<String, Object>> loadPDay(LocalDate startDate, LocalDate endDate) {
        Map<String, Map<String, Object>> result = new TreeMap<>();

        // читаем партии из PLR_PDAYNP
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(LOAD_PDAY)) {
            ps.setString(1, startDate.toString() + "T00:00:00");     // Параметр для v.DTI start
            ps.setString(2, endDate.toString() + "T00:00:00");     // Параметр для v.DTI end
            ps.setString(3, "0119030000");                           // Параметр для v.KSK

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>(); // сохранение порядка колонок
                // KSK, KMC, DTI, NP, KOLEV, UX, SNPZ, MASSA
                String kmc = rs.getString(2);
                java.sql.Date dti = rs.getDate(3);
                java.sql.Date dtf = rs.getDate(4);
                int np = rs.getInt(5);
                int kolev = rs.getInt(6);
                int ux = rs.getInt(7);
                int isnpz = rs.getInt(8);
                String ssnpz = rs.getString(8);
                int massa = rs.getInt(9);

                row.put("SNM", rs.getString(1));
                row.put("KMC", kmc);
                row.put("DTI", dti);
                row.put("DTF", dtf);
                row.put("NP", np);
                row.put("KOLEV", kolev);
                row.put("UX", ux);
                row.put("SNPZ", isnpz);
                row.put("MASSA", massa);

                result.put(ssnpz, row);
            }
        } catch (SQLException e) {
            // можно логировать e
            throw new RuntimeException("Failed to load jobs from PLR_PDAYNP. " + e.getMessage(), e);
        }

        // читаем партии из BD_VZPMC
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(LOAD_VZPMC)) {
            ps.setString(1, startDate.toString() + "T00:00:00");     // Параметр для v.DTI start
            ps.setString(2, endDate.toString() + "T00:00:00");       // Параметр для v.DTI end
            ps.setString(3, "0119030000");                           // Параметр для v.KSK
            ps.setDouble(4, 0.1);                                    // Параметр для m.MASSA

            ResultSet rs = ps.executeQuery();

            PreparedStatement stmt = conn.prepareStatement(INSERT_PDAY);

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>(); // сохранение порядка колонок
                // KSK, KMC, DTI, NP, KOLEV, UX, SNPZ, MASSA
                String ksk = "0119030000";
                String kmc = rs.getString(2);
                java.sql.Date dti = rs.getDate(3);
                int np = rs.getInt(5);
                int kolev = rs.getInt(6);
                int ux = rs.getInt(7);
                int isnpz = rs.getInt(8);
                String ssnpz = rs.getString(8);
                int massa = rs.getInt(9);

                row.put("SNM", rs.getString(1));
                row.put("KMC", kmc);
                row.put("DTI", dti);
                row.put("DTF", "");
                row.put("NP", np);
                row.put("KOLEV", kolev);
                row.put("UX", ux);
                row.put("SNPZ", isnpz);
                row.put("MASSA", massa);

                if (!result.containsKey(ssnpz)) {
                    result.put(ssnpz, row);
                    stmt.setInt(1, isnpz);
                    stmt.setString(2, ksk);
                    stmt.setString(3, kmc);
                    stmt.setDate(4, dti);
                    stmt.setInt(5, np);
                    stmt.setInt(6, kolev);
                    stmt.setInt(7, ux);
                    stmt.setInt(8, isnpz);
                    stmt.setInt(9, massa);
//                    int updatedRows = stmt.executeUpdate();

                    long startTime = System.currentTimeMillis();
                    int updatedRows = stmt.executeUpdate();
                    long endTime = System.currentTimeMillis();
                    long durationMs = endTime - startTime;


                    String insertLog = String.format(
                            "[%s] rows=%d INSERT INTO PLR_PDAYNP (SNPZ, KSK, KMC, DTI, NP, KOLEV, UX, SNPZ_DUP, MASSA) " +
                                    "VALUES (%d, '%s', '%s', '%s', %d, %d, %d, %d, %d); -- time=%d ms",
                            java.time.LocalDateTime.now(),
                            updatedRows,
                            isnpz,
                            ksk,
                            kmc,
                            dti.toString(),
                            np,
                            kolev,
                            ux,
                            isnpz,
                            massa,
                            durationMs
                    );

                    try (FileWriter fw = new FileWriter("queries.log", true);
                         BufferedWriter bw = new BufferedWriter(fw);
                         PrintWriter out = new PrintWriter(bw)) {
                        out.println(insertLog);
                    } catch (IOException e) {
                        log.error("Failed to write queries.log: {}", insertLog, e);
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load jobs from BD_VZPMC. " + e.getMessage(), e);
        }

        return result;

    }


    public void updatePDay(Map<String, LocalDate> mapsnpz) {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(UPDATE_PDAYDTF)) {

            for (Map.Entry<String, LocalDate> entry : mapsnpz.entrySet()) {
                stmt.setDate(1, java.sql.Date.valueOf(entry.getValue()));
                stmt.setString(2, entry.getKey());
                stmt.addBatch();
            }

            stmt.executeBatch();
            log.info("Successfully UPDATE_PDAYDTF");
        } catch (SQLException e) {
            log.error("Error UPDATE_PDAYDTF", e);
            throw new RuntimeException("Failed to update jobs to PLR_PDAYNP " + e.getMessage(), e);
        }
    }

    public void sendToWork(List<Job> jobs) {

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(false); // ручное управление транзакцией для атомарности

            try (PreparedStatement ps = conn.prepareStatement(UPDATE_WORK)) {
                for (Job job : jobs) {
                    ps.setString(1, job.getLine().getId());
                    ps.setObject(2, job.getStartProductionDateTime());
                    ps.setObject(3, job.getEndDateTime());
                    ps.setLong(4, job.getDuration().toMinutes());
                    ps.setInt(5, job.getSnpz());
                    ps.addBatch();
                }
                ps.executeBatch();

                try (PreparedStatement proc = conn.prepareStatement(REFRESH_FASP)) {
                    proc.setString(1, "6000000");
                    proc.setString(2, "0119030000");
                    proc.execute();
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw e;
                }

                conn.commit(); // фиксируем транзакцию
                log.info("Successfully UPDATE_WORK");
            }
        } catch (SQLException e) {
            log.error("Error UPDATE_WORK, rollback", e);
            if (conn != null) {
                try {
                    conn.rollback(); // откатываем
                    log.warn("Rollback UPDATE_WORK");
                } catch (SQLException rollbackEx) {
                    log.error("Error rollback", rollbackEx);
                }
            }
            throw new RuntimeException("Error UPDATE_WORK, rollback", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    log.debug("Close connection UPDATE_WORK");
                } catch (Exception closeEx) {
                    log.error("Error close connection UPDATE_WORK", closeEx);
                }
            }
        }
    }
}
