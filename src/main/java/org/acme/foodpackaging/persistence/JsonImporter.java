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
import java.time.LocalDate;
import java.util.List;

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
                if (rs.next()) {
                    String json = rs.getString("PLAN");
                    if (json == null) {
                        throw new IllegalStateException("PLAN column is null for DT=" + dt);
                    }
                    PackagingSchedule schedule;
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    objectMapper.registerModule(new JavaTimeModule());
                    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    schedule = objectMapper.readValue(json, PackagingSchedule.class);

                    CleaningCalculator calculator = new CleaningCalculator();
                    calculator.cleaningCalculate(schedule.getProducts());

                    for(Job job : schedule.getJobs()){
                        for(Product product : schedule.getProducts()){
                            if(job.getProduct().getId().equals(product.getId())){
                                job.getProduct().setCleaningDurations(product.getCleaningDurations());
                            }
                        }
                    }

                    return schedule;
                } else {
                    throw new IllegalStateException("No saved schedule found for DT=" + dt);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to import PackagingSchedule from DB", e);
        }
    }
}
