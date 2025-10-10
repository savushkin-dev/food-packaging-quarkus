package org.acme.foodpackaging.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        Map<String, Job> nextMap = schedule.getJobs().stream()
                .filter(j -> j.getNextJob() != null)
                .collect(Collectors.toMap(j -> j.getNextJob().getId(), Function.identity()));

        Map<String, Job> previousMap = schedule.getJobs().stream()
                .filter(j -> j.getPreviousJob() != null)
                .collect(Collectors.toMap(j -> j.getPreviousJob().getId(), Function.identity()));

        for (Job job : schedule.getJobs()) {
            if (job.getPreviousJob() == null) {
                job.setPreviousJob(previousMap.get(job.getId()));
            }

            if (job.getNextJob() == null) {
                job.setNextJob(nextMap.get(job.getId()));
            }

            job.setPreviousJobId(job.getPreviousJob() != null ? job.getPreviousJob().getId() : null);
            job.setNextJobId(job.getNextJob() != null ? job.getNextJob().getId() : null);
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
