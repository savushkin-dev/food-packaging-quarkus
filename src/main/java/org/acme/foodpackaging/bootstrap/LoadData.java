package org.acme.foodpackaging.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.apache.commons.math3.util.Pair;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import static org.acme.foodpackaging.sql.SqlQueries.LOAD_JOBS;
import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LINES_SPEEDS;

import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class LoadData {
    @Inject
    PackagingScheduleRepository repository;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    private LocalDateTime IDEAL_END_DATE_TIME;
    private LocalDateTime MAX_END_DATE_TIME;
    private LocalDateTime MIN_START_DATE_TIME;

    public void loadDataByDate(LocalDate START_DATE, LocalDate END_DATE, LocalDateTime idealEndDateTime,
                               LocalDateTime maxEndDateTime, Map<Integer, LocalDateTime> lineStartsTime) {
        this.MIN_START_DATE_TIME = Collections.min(lineStartsTime.values());
        this.IDEAL_END_DATE_TIME = idealEndDateTime;
        this.MAX_END_DATE_TIME = maxEndDateTime;
        PackagingSchedule solution = initSolution(START_DATE, END_DATE, lineStartsTime);
        repository.write(solution);
    }

    @Transactional
    public PackagingSchedule initSolution(LocalDate START_DATE, LocalDate END_DATE,
                                          Map<Integer, LocalDateTime> lineStartsTime) {
        PackagingSchedule solution = new PackagingSchedule();
        // Инициализация даты
        solution.setWorkCalendar(new WorkCalendar(START_DATE, END_DATE));
        // Инициализация линий
        List<Line> lines = createLines(lineStartsTime.size(), lineStartsTime);
        List<Product> products = new ArrayList<>();
        List<Job> jobs = loadJobs(String.valueOf(START_DATE), MIN_START_DATE_TIME, products);
        // Инициализация времени мойки между продукцией
        CleaningTimeCalculator cleaningCalculator = new CleaningTimeCalculator(products);

        solution.setLines(lines);
        solution.setProducts(products);
        jobs.sort(Comparator.comparing(Job::getName));
        solution.setJobs(jobs);

        return solution;
    }

    private Map<Integer, Map<String, Integer>> loadSpeedsFromDB() {
        Map<Pair<String, String>, Integer> mapSpeed = getSpeedsfromDB();
        Map<Integer, Map<String, Integer>> finalMap = new HashMap<>();

        Set<String> allTypes = new HashSet<>();
        for (Pair<String, String> key : mapSpeed.keySet()) {
            allTypes.add(key.getSecond());
        }

        for (Map.Entry<Pair<String, String>, Integer> entry : mapSpeed.entrySet()) {
            Pair<String, String> key = entry.getKey();
            Integer line = Integer.valueOf(extractLineNumberRegex(key.getFirst()));
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

    private List<Job> loadJobs(String date, LocalDateTime startDateTime, List<Product> products) {
        List<Job> jobs = new ArrayList<>();
        ProductFactory productFactory = new ProductFactory();
        Map<String, Product> productMap = new HashMap<>();

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
                        int quantity = resultSet.getInt("KOLEV");       // количество
                        String np = resultSet.getString("NP");         // Номер партии
                        String priority = resultSet.getString("UX");  // Приоритет выполнения
                        String ean13 = resultSet.getString("EAN13"); // Уникальный идентификатор продукта
                        String name = resultSet.getString("NAME");  // Название
                        String shortName = resultSet.getString(("SNM"));      // Сокращенное название
                        // Список с продукцией хранит только уникальные значения
                        Product product = productMap.get(ean13);
                        if (product == null) {
                            product = productFactory.create(ean13, name);
                            productMap.put(ean13, product);
                            products.add(product);
                        }
                        // Создание партий
                        Job job = createJob(
                                String.valueOf(++job_id), cleanSyrkiName(shortName), np, product, quantity,
                                MIN_START_DATE_TIME, IDEAL_END_DATE_TIME, MAX_END_DATE_TIME
                        );
                        jobs.add(job);
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load jobs from DB", e);
        }

        Map<Integer, Map<String, Integer>> lineSpeeds = loadSpeedsFromDB();

        for(Job job : jobs){
            job.setLineSpeeds(lineSpeeds);
        }
        return jobs;
    }

    private List<Line> createLines(int lineCount, Map<Integer, LocalDateTime> lineStartsTime){
        List<Line> lines = new ArrayList<>(lineCount);
        for( Map.Entry<Integer, LocalDateTime> entry : lineStartsTime.entrySet()){
                String lineName = "Line" + entry.getKey();
                Line line = new Line(String.valueOf(entry.getKey()), lineName, entry.getValue());
                lines.add(line);
        }
        return lines;
    }

    private Job createJob(String id, String jobName, String np, Product product, int quantity,
                          LocalDateTime minStartDateTime, LocalDateTime idealEndDateTime, LocalDateTime maxEndDateTime) {
        return new Job (id, jobName, np, product, quantity,
                    minStartDateTime,
                    idealEndDateTime,
                    maxEndDateTime, false
        );
    }

    public static String cleanSyrkiName(String input) {
        return input.replaceFirst(
                "(?i)Сырок\\s*(тв\\.\\s*г\\.с|тв\\.\\s*гл\\.с|тв\\.\\s*гл\\.|тв\\.\\s*г\\.|гл\\.|тв\\.\\s*глазированный|глазированный|тв\\.\\s*глазир\\.)",
                ""
        ).trim();
    }

    public static String extractLineNumberRegex(String code) {
        Pattern p = Pattern.compile("(?<=1706100)(\\d+?)(?=0+$)");
        Matcher m = p.matcher(code);
        if (m.find()) {
            String num = m.group(1).replaceFirst("^0+", "");
            return num.isEmpty() ? "0" : num;
        }
        throw new IllegalArgumentException("Неверный формат кода: " + code);
    }
}
