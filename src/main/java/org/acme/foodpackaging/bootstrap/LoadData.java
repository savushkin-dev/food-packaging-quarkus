package org.acme.foodpackaging.bootstrap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.CleaningTimeToExcel;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.apache.commons.math3.util.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;

import java.time.*;
import java.util.*;


import static org.acme.foodpackaging.sql.SqlQueries.*;

@ApplicationScoped
public class LoadData {
    @Inject
    PackagingScheduleRepository repository;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    private LocalDateTime IDEAL_END_DATE_TIME;
    private LocalDateTime MAX_END_DATE_TIME;
    private LocalDateTime MIN_START_DATE_TIME;
    private Map<String, Product> allProductsMap;
    private Map<String, String> linesIdWithNamesMap;
    private CleaningCalculator calculator;

    @PostConstruct
    private void init(){
        this.linesIdWithNamesMap = loadLinesIdWithNames();
    }

    public void loadDataByDate(LocalDate START_DATE, LocalDate END_DATE, LocalDateTime idealEndDateTime,
                               LocalDateTime maxEndDateTime, Map<String, LocalDateTime> lineStartsTime) {
        this.MIN_START_DATE_TIME = Collections.min(lineStartsTime.values());
        this.IDEAL_END_DATE_TIME = idealEndDateTime;
        this.MAX_END_DATE_TIME = maxEndDateTime;
        this.allProductsMap = loadProductfromDB();
        this.calculator = new CleaningCalculator();
        PackagingSchedule solution = initSolution(START_DATE, END_DATE, lineStartsTime);
        repository.write(solution);
    }

    @Transactional
    public PackagingSchedule initSolution(LocalDate START_DATE, LocalDate END_DATE,
                                          Map<String, LocalDateTime> lineStartsTime) {
        PackagingSchedule solution = new PackagingSchedule();
        // Инициализация даты
        solution.setWorkCalendar(new WorkCalendar(START_DATE, END_DATE));
        // Инициализация линий
        List<Line> lines = createLines(lineStartsTime.size(), lineStartsTime);
        Set<Product> productSet = new HashSet<>();
        List<Job> jobs = loadJobs(String.valueOf(START_DATE), MIN_START_DATE_TIME, productSet);
        List<Product> products = new ArrayList<>(productSet);
        Product maintenanceProduct = new Product(
                "Maintenance Product",
                 "MAINTENANCE",
                "",
                "",
                "",
                "",
                "",
                ""
        );
        products.add(maintenanceProduct);
        // Инициализация времени мойки между продукцией
        calculator.cleaningCalculate(products);
        solution.setLines(lines);
        solution.setProducts(products);
        solution.setJobs(jobs);

        return solution;
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

    private void exportCleaningTime(){
        List<Product> allProducts = allProductsMap.values().stream().toList();
        calculator.cleaningCalculate(allProducts);
        try {
            CleaningTimeToExcel cleaningTimeToExcel = new CleaningTimeToExcel(allProducts);
        }
        catch (Exception e){
            System.err.println(e.getMessage());
        }
    }

    private List<CleaningRule> loadCleaningRulesfromDB(){

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
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to load cleaning rules from DB", e);
        }
        return rules;
    }

    private Map<Pair<String, String>, Integer> getSpeedsfromDB(){
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
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to load lines from DB", e);
        }
        return lines;
    }

private Map<String, Product> loadProductfromDB(){
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
    }
    catch (SQLException e) {
        throw new RuntimeException("Failed to load products from DB", e);
    }
    return productsMap;
}

    private List<Job> loadJobs(String date, LocalDateTime startDateTime, Set<Product> productsSet) {
        List<Job> jobs = new ArrayList<>();

        try {
            try (Connection connection = DriverManager.getConnection(dbUrl);
                 PreparedStatement preparedStatement = connection.prepareStatement(LOAD_JOBS)) {
                preparedStatement.setString(1, date + "T00:00:00");     // Параметр для v.DTI
                preparedStatement.setString(2, "0119030000");          // Параметр для v.KSK
                preparedStatement.setDouble(3, 0.1);                  // Параметр для m.MASSA

                int job_id = 0;
                Duration duration;
                try (ResultSet resultSet = preparedStatement.executeQuery()) {

                    while (resultSet.next()) {
                        int quantity = resultSet.getInt("KOLEV");          // количество
                        int np = resultSet.getObject("NP") != null ? resultSet.getInt("NP") : 0;
                        int priority =resultSet.getObject("UX") != null ? resultSet.getInt("UX") : 0;// Приоритет выполнения
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
                                String.valueOf(++job_id), cleanSyrkiName(shortName), np, product, quantity, priority,
                                MIN_START_DATE_TIME, IDEAL_END_DATE_TIME, MAX_END_DATE_TIME
                        );
                        jobs.add(job);
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load jobs from DB", e);
        }

        Map<String, Map<String, Integer>> lineSpeeds = loadSpeedsFromDB();

        for(Job job : jobs){
            job.setLineSpeeds(lineSpeeds);
        }
        return jobs;
    }

    private List<Line> createLines(int lineCount, Map<String, LocalDateTime> lineStartsTime){
        List<Line> lines = new ArrayList<>(lineCount);
        for( Map.Entry<String, LocalDateTime> entry : lineStartsTime.entrySet()){
                String lineName = linesIdWithNamesMap.get(entry.getKey());
                Line line = new Line(entry.getKey(), lineName, entry.getValue());
                lines.add(line);
        }
        return lines;
    }

    private Map<String, String> loadLinesIdWithNames(){
        Map<String, String> linesNamesById = new LinkedHashMap<>();
        ResultSet resultSet = null;
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();) {
            resultSet = statement.executeQuery(LOAD_LINES_WITH_NAME);

            while (resultSet.next()) {
                String lineId = resultSet.getString("KRC");
                String lineName = resultSet.getString("SNM");
                linesNamesById.put(lineId, lineName);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to load lines id with names from DB", e);
        }
        return linesNamesById;
    }

    public Map<String, String> getLinesIdWithNamesMap(){
        return linesIdWithNamesMap;
    }
    private Job createJob(String id, String jobName, int np, Product product, int quantity, int priority,
                          LocalDateTime minStartDateTime, LocalDateTime idealEndDateTime, LocalDateTime maxEndDateTime) {
        return new Job (id, jobName, np, product, quantity,
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
                    stmt.setString(1, ksk);
                    stmt.setString(2, kmc);
                    stmt.setDate(3, dti);
                    stmt.setInt(4, np);
                    stmt.setInt(5, kolev);
                    stmt.setInt(6, ux);
                    stmt.setInt(7, isnpz);
                    stmt.setInt(8, massa);
                    int updatedRows = stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            // можно логировать e
            throw new RuntimeException("Failed to load jobs from BD_VZPMC. " + e.getMessage(), e);
        }

        return result;

    }

    public void updatePDay(LocalDate startDate, LocalDate endDate, Map<String, LocalDate> mapsnpz) {

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(UPDATE_PDAYDTF)) {

            for (Map.Entry<String, LocalDate> entry : mapsnpz.entrySet()) {
                String key = entry.getKey();
                LocalDate value = entry.getValue();
                stmt.setDate(1, java.sql.Date.valueOf(value));
                stmt.setString(2, key);
                int updatedRows = stmt.executeUpdate();
            }
        } catch (SQLException e) {
            // можно логировать e
            throw new RuntimeException("Failed to update jobs to PLR_PDAYNP "+e.getMessage(), e);
        }
    }



}
