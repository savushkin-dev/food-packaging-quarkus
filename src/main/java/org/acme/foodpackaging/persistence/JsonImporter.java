package org.acme.foodpackaging.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.acme.foodpackaging.domain.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.sql.SqlQueries.SELECT_SOLUTION_FROM_JSON;

public class JsonImporter {
    private final String dbUrl;
    private final ObjectMapper objectMapper;
    private final LocalDate dt;

    public JsonImporter(String dbUrl, LocalDate dt) {
        this.dbUrl = dbUrl;
        this.dt = dt;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public PackagingSchedule importFromDb() {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(SELECT_SOLUTION_FROM_JSON)) {

            ps.setDate(1, java.sql.Date.valueOf(dt));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No saved schedule found for DT=" + dt);
                }

                String json = rs.getString("PLAN");
                if (json == null) {
                    throw new IllegalStateException("PLAN column is null for DT=" + dt);
                }

                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                PackagingSchedule schedule = objectMapper.readValue(json, PackagingSchedule.class);

                CleaningCalculator calculator = new CleaningCalculator();
                calculator.cleaningCalculate(schedule.getProducts());

                Map<String, Product> productMap = schedule.getProducts().stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

                for (Job job : schedule.getJobs()) {
                    Product productInMap = productMap.get(job.getProduct().getId());
                    if (productInMap == null) {
                        throw new IllegalStateException("Job product not found in products list: "
                                + job.getProduct().getId());
                    }
                    job.setProduct(productInMap);
                }

                Map<String, List<Job>> jobsByLineName = schedule.getJobs().stream()
                        .collect(Collectors.groupingBy(job -> job.getLine().getName()));

                for (Line line : schedule.getLines()) {
                    List<Job> jobsForLine = jobsByLineName.getOrDefault(line.getName(), new ArrayList<>());

                    for (Job job : jobsForLine) {
                        job.setLine(line);
                    }

                    line.setJobs(jobsForLine);
                }

                for (Job prevJob : schedule.getJobs()) {
                    for (Job nextJob : schedule.getJobs()) {
                        Duration duration = prevJob.getProduct().getCleaningDurations()
                                .get(nextJob.getProduct());
                        if (duration == null) {
                            throw new IllegalStateException(
                                    "Missing cleaning duration from product " +
                                            prevJob.getProduct().getId() + " to product " +
                                            nextJob.getProduct().getId());
                        }
                    }
                }
                return schedule;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to import PackagingSchedule from DB", e);
        }
    }
}