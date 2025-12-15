package org.acme.foodpackaging.persistence.scheduleSaving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import static org.acme.foodpackaging.sql.SqlQueries.UPSERT_SOLUTION_TO_JSON;

@ApplicationScoped
public class SolutionExport {

    @ConfigProperty(name = "db.url")
    String dbUrl;

    private final ObjectMapper mapper;
    public SolutionExport() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void export(PackagingSchedule schedule) throws Exception {
        if (schedule == null) throw new IllegalArgumentException("Schedule is null");

        for (Job job : schedule.getJobs()) {
            String prevId = job.getPreviousJob() != null ? job.getPreviousJob().getId() : null;
            String nextId = job.getNextJob() != null ? job.getNextJob().getId() : null;

            job.setPreviousJobId(prevId);
            job.setNextJobId(nextId);
        }

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
