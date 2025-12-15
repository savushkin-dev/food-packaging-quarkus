package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.factory.JobFactory;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JobRepository {

    @Inject
    JobFactory jobFactory;
    @Inject
    LoadDataService loadDataService;
    @Inject
    JobDBLoader jobDBLoader;

    public List<Job> loadJobs(LocalDate date) {
        LocalDateTime from = date.atStartOfDay().minusDays(1);
        LocalDateTime to = from.plusDays(2);

        List<DbJobRow> rows =
                jobDBLoader.loadJobRows(
                        from, to, "0119030000"
                );

        List<Job> jobs = new ArrayList<>();

        for (DbJobRow r : rows) {

            Product product =
                    loadDataService.getProducts().get(r.kmc());

            if (product == null) {
                throw new IllegalStateException(
                        "Unknown product KMC=" + r.kmc());
            }
int id =0;
            Job job = jobFactory.createJob(
                    String.valueOf(++id),
                    jobFactory.nameCleaner(r.shortName()), id, safe(r.np()), product,
                    r.mass(), safe(r.quantity()), safe(r.priority()),
                    from, from.plusHours(2), to
            );

            jobs.add(job);
        }

        return jobs;
    }

    private int safe(Integer v) {
        return v != null ? v : 0;
    }

    private double safe(Double v) {
        return v != null ? v : 0.0;
    }
}


