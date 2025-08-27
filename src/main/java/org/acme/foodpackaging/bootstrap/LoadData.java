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
import java.time.*;
import java.util.*;

@ApplicationScoped
public class LoadData {
    @Inject
    PackagingScheduleRepository repository;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    private LocalDateTime IDEAL_END_DATE_TIME;
    private LocalDateTime MAX_END_DATE_TIME;
    private LocalDateTime MIN_START_DATE_TIME;

    public void loadDataByDate(LocalDate START_DATE,  LocalDate END_DATE, LocalDateTime idealEndDateTime,
                               LocalDateTime maxEndDateTime, Map<Integer, LocalDateTime> lineStartsTime) {
        this.MIN_START_DATE_TIME =  Collections.min(lineStartsTime.values());
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

    private Map<Integer, Map<String, Integer>> loadSpeedsFromDB(){
        Map<Integer, Map<String, Integer>> lineSpeeds = new HashMap<>();
        try {
            FileInputStream fis = new FileInputStream("src/main/resources/line_speeds.xlsx");
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);
            List<String> productTypes = new ArrayList<>();
            for (int j = 1; j < headerRow.getLastCellNum(); j++) {
                productTypes.add(headerRow.getCell(j).getStringCellValue());
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                int lineId = (int) row.getCell(0).getNumericCellValue();
                Map<String, Integer> speeds = new HashMap<>();

                for (int j = 1; j < row.getLastCellNum(); j++) {
                    int speed = (int) row.getCell(j).getNumericCellValue();
                    speeds.put(productTypes.get(j - 1), speed);
                }

                lineSpeeds.put(lineId, speeds);
            }

            workbook.close();
            fis.close();
        }
        catch (IOException e) {
            System.err.println("Error: The specified file was not found. " + e.getMessage());
        }
        return lineSpeeds;

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
}
