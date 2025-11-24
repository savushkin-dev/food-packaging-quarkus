package org.acme.foodpackaging.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.acme.foodpackaging.domain.*;

import java.io.IOException;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
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

    private static <T> Map<String, T> mapById(Collection<T> list, Function<T, String> keyExtractor) {
        if (list == null) return Collections.emptyMap();
        return list.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(keyExtractor, Function.identity(), (a, b) -> a));
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

                Map<String, Product> productMap = mapById(schedule.getProducts(), Product::getId);
                Product maintenanceProduct = schedule.getProducts().stream()
                        .filter(p -> "MAINTENANCE".equalsIgnoreCase(p.getId()))
                        .findFirst()
                        .orElseGet(() -> {
                            Product p = new Product(
                                    "Maintenance Product",
                                    "MAINTENANCE",
                                    "", "", "", "", "", ""
                            );
                            schedule.getProducts().add(p);
                            return p;
                        });

                Map<String, Line> lineMap = mapById(schedule.getLines(), Line::getId);
                Map<String, Job> jobMap = mapById(schedule.getJobs(), Job::getId);

                for (Job job : schedule.getJobs()) {

                    if (job.getProduct() != null) {
                        Product product = productMap.get(job.getProduct().getId());
                        if (product != null) job.setProduct(product);
                        else throw new IllegalStateException("Missing product for job " + job.getId());
                    }

                    if (job.getLine() != null) {
                        Line line = lineMap.get(job.getLine().getId());
                        if (line != null) job.setLine(line);
                    }

                    if (job.getPreviousJobId() != null) {
                        job.setPreviousJob(jobMap.get(job.getPreviousJobId()));
                    }
                    if (job.getNextJobId() != null) {
                        job.setNextJob(jobMap.get(job.getNextJobId()));
                    }
                }

                for (Line line : schedule.getLines()) {
                    List<Job> jobsForLine = schedule.getJobs().stream()
                            .filter(j -> j.getLine() != null && j.getLine().getId().equals(line.getId()))
                            .sorted(Comparator.comparing(Job::getStartProductionDateTime,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();
                    line.setJobs(jobsForLine);
                }

                new CleaningCalculator().cleaningCalculate(schedule.getProducts());

                validateCleaningDurations(schedule);
                debugReferenceIntegrity(schedule);

                return schedule;
            }

        } catch (IOException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while importing schedule", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while importing schedule", e);
        }
    }

    private void validateCleaningDurations(PackagingSchedule schedule) {
        for (Product from : schedule.getProducts()) {
            for (Product to : schedule.getProducts()) {
                Duration d = from.getCleaningDurations().get(to);
                if (d == null) {
                    throw new IllegalStateException(String.format(
                            "Missing cleaning duration: from %s to %s",
                            from.getId(), to.getId()
                    ));
                }
            }
        }
    }

    private void debugReferenceIntegrity(PackagingSchedule schedule) {
        Map<String, Product> productMap = mapById(schedule.getProducts(), Product::getId);
        for (Job job : schedule.getJobs()) {
            if (job.getProduct() != null) {
                Product expected = productMap.get(job.getProduct().getId());
                if (expected != job.getProduct()) {
                    System.err.printf("Mismatch: Job %s references cloned product %s%n",
                            job.getId(), job.getProduct().getId());
                }
            }
        }
    }
}
