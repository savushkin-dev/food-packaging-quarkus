package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.entity.NS_McEntity;
import org.acme.foodpackaging.entity.jobs.JobEntity;
import org.acme.foodpackaging.factory.JobFactory;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.persistence.load.LoadDataService;

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

    public List<Job> loadJobs(LocalDate date,
                              LocalDateTime minStart,
                              LocalDateTime idealEnd,
                              LocalDateTime maxEnd) {

        LocalDateTime targetDate = date.atStartOfDay();
        String ksk = "0119030000";
        double maxMass = 0.1;

        List<JobEntity> rows = jobDBLoader.loadJobs(targetDate, ksk, maxMass);

        List<Job> jobs = new ArrayList<>();
        int jobId = 0;

        for (JobEntity v : rows) {

            if (v.np == null || v.np.intValue() == 0) continue;

            NS_McEntity m = v.mc;

            int np = v.np.intValue();
            int quantity = v.quantity != null ? v.quantity : 0;
            int priority = v.priority != null ? v.priority : 0;
            int snpz = v.snpz != null ? v.snpz : 0;
            double mass = v.massa != null ? v.massa : 0.0;

            Product product = loadDataService.getProducts().get(v.kmc);
            if (product == null) {
                throw new IllegalStateException("Unknown product KMC=" + v.kmc);
            }

            Job job = jobFactory.createJob(
                    String.valueOf(++jobId),
                    jobFactory.nameCleaner(m.shortName),
                    snpz, np,
                    product, mass, quantity, priority,
                    minStart, idealEnd, maxEnd
            );

            jobs.add(job);
        }
        return jobs;
    }
}

