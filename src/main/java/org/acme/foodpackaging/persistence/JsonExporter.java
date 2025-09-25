package org.acme.foodpackaging.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import static org.acme.foodpackaging.sql.SqlQueries.UPSERT_SOLUTION_TO_JSON;

public class JsonExporter {
    private final ObjectMapper mapper;
    private final String dbUrl;

    public JsonExporter(String dbUrl) {
        this.dbUrl = dbUrl;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void export(PackagingSchedule schedule) throws Exception {
        if (schedule == null) throw new IllegalArgumentException("Schedule is null");

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schedule);
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(UPSERT_SOLUTION_TO_JSON)) {

            ps.setString(1, "170610000000");

            LocalDate dt = schedule.getWorkCalendar().getFromDate();
            ps.setDate(2, java.sql.Date.valueOf(dt));

            ps.setString(3, json);

            ps.executeUpdate();
        }
    }
}
