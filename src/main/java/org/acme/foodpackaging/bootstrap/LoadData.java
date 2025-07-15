package org.acme.foodpackaging.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import static org.acme.foodpackaging.sql.SqlQueries.LOAD_JOBS;
import java.time.*;
import java.util.*;

@ApplicationScoped
public class LoadData {
    @Inject
    PackagingScheduleRepository repository;

    @ConfigProperty(name = "demo-data.line-count", defaultValue = "6")
    int lineCount;
    @ConfigProperty(name = "db.url")
    String dbUrl;

    private static final int DEFAULT_PRIORITY = 0;

    private ProductNameShortener shortener;

    public void loadDataByDate(String dateString) {
        PackagingSchedule solution = initSolution(dateString);
        repository.write(solution);
    }

    @Transactional
    public PackagingSchedule initSolution(String date) {
        Objects.requireNonNull(date, "Date cannot be null");
        final LocalDate START_DATE = LocalDate.parse(date);
        final LocalDateTime START_DATE_TIME = LocalDateTime.of(START_DATE, LocalTime.of(8, 0));
        final LocalDate END_DATE = START_DATE.plusDays(1);
        final LocalDateTime END_DATE_TIME = LocalDateTime.of(END_DATE, LocalTime.of(4, 0));

        PackagingSchedule solution = new PackagingSchedule();
        DurationProvider provider = new DurationProvider();
        this.shortener = new ProductNameShortener();
        // Инициализация даты
        solution.setWorkCalendar(new WorkCalendar(START_DATE, END_DATE));
        // Инициализация линий
        List<Line> lines = createLines(lineCount, START_DATE_TIME);
        List<Product> products = new ArrayList<>();
        List<Job> jobs = loadJobs(date,  START_DATE_TIME, provider, products);
        // Инициализация времени мойки между продукцией
        CleaningTimeCalculator cleaningCalculator = new CleaningTimeCalculator(products);

        solution.setLines(lines);
        solution.setProducts(products);
        jobs.sort(Comparator.comparing(Job::getName));
        solution.setJobs(jobs);
        return solution;
    }

    private List<Job> loadJobs(String date, LocalDateTime startDateTime, DurationProvider provider, List<Product> products) {
        List<Job> jobs = new ArrayList<>();
        ProductFactory productFactory = new ProductFactory();
        Map<String, Product> productMap = new HashMap<>();

        try {
            try (Connection connection = DriverManager.getConnection(dbUrl);
                 PreparedStatement preparedStatement = connection.prepareStatement(LOAD_JOBS)) {
                preparedStatement.setString(1, date + "T00:00:00");     // Параметр для v.DTI

                int job_id = 0;
                Duration duration;
                try (ResultSet resultSet = preparedStatement.executeQuery()) {

                    while (resultSet.next()) {
                        int quantity = resultSet.getInt("KOLEV");    // количество
                        String np = resultSet.getString("NP");       // Номер партии
                        String priority = resultSet.getString("UX"); // Приоритет выполнения
                        String ean13 = resultSet.getString("EAN13"); // Уникальный идентификатор продукта
                        String name = resultSet.getString("NAME");   // Название
                        // Список с продукцией хранит только уникальные значения
                        Product product = productMap.get(ean13);
                        if (product == null) {
                            product = productFactory.create(ean13, name);
                            productMap.put(ean13, product);
                            products.add(product);
                        }
                     switch(product.getType()){ // Если тип не классика, можно время выполнения сразу рассчитать
                         case ROD, CACTUS, PLUSH -> duration = provider.calculate(product, quantity);
                         default -> duration = Duration.ZERO;
                     }
                        // Создание партий
                        Job job = createJob(
                                String.valueOf(++job_id), np, product, quantity,
                                duration, provider,
                                DEFAULT_PRIORITY, startDateTime
                        );
                        jobs.add(job);
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load jobs from DB", e);
        }
        return jobs;
    }

    private List<Line> createLines(int lineCount, LocalDateTime startDateTime){
        List<Line> lines = new ArrayList<>(lineCount);
        for(int i=1; i<=lineCount; ++i){
            String lineName = "Line" + String.valueOf(i);
            Line line = new Line(String.valueOf(i), lineName, startDateTime);
            lines.add(line);
        }
        return lines;
    }

    private Job createJob(String id, String np, Product product, int quantity, Duration duration, DurationProvider provider, int priority, LocalDateTime startDate) {
        String jobName = shortener.getShortName(product.getId(), product.getName());
        return new Job(id, jobName, np, product, quantity, duration, provider, startDate,
                startDate.plusDays(1).withHour(2).withMinute(0), // Идеальное время завершения
                startDate.plusDays(1).withHour(4).withMinute(0), // Максимальное время завершения
                priority, false
        );
    }
}
