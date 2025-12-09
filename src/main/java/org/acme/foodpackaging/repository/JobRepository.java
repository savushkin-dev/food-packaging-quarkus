package org.acme.foodpackaging.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.factory.JobFactory;
import org.acme.foodpackaging.service.load.LoadDataService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_JOBS_FOR_SELECTED_DATE;

@ApplicationScoped
public class JobRepository {
    @Inject
    JobFactory jobFactory;
    @Inject
    LoadDataService loadDataService;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    public List<Job> loadJobs(LocalDate date, LocalDateTime minStart, LocalDateTime idealEnd, LocalDateTime maxEnd) {
        List<Job> jobs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(LOAD_JOBS_FOR_SELECTED_DATE)) {

            ps.setObject(1, date.atStartOfDay());
            ps.setString(2, "0119030000");
            ps.setDouble(3, 0.1);

            int jobId = 0;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal npVal = rs.getBigDecimal("NP");
                    if (npVal == null || npVal.intValue() == 0) continue;

                    int np = npVal.intValue();
                    int quantity = rs.getInt("KOLEV");
                    int priority = rs.getObject("UX") != null ? rs.getInt("UX") : 0;
                    int snpz = rs.getInt("SNPZ");
                    double mass = rs.getDouble("MASSA");
                    String kmc = rs.getString("KMC");
                    String shortName = rs.getString("SNM");

                    Product product = loadDataService.getProducts().get(kmc);
                    if (product == null) {
                        throw new IllegalStateException("Unknown product KMC=" + kmc);
                    }

                    Job job = jobFactory.createJob(
                            String.valueOf(++jobId),
                            jobFactory.nameCleaner(shortName), snpz, np,
                            product, mass, quantity, priority,
                            minStart, idealEnd, maxEnd
                    );
                    jobs.add(job);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load jobs from DB", e);
        }
        return jobs;
    }
}
