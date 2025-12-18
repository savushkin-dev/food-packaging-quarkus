package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.factory.JobFactory;
import org.acme.foodpackaging.persistence.db.JobDBLoader;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class JobRepository {

    @Inject
    JobFactory jobFactory;
    @Inject
    LoadDataService loadDataService;
    @Inject
    JobDBLoader jobDBLoader;
    @Setter
    @Getter
    private Map<Integer, DbJobRow> dbJobRowMap;
    @Setter
    @Getter
    private  Map<Integer, Job> jobIdMap;
    @Setter
    @Getter
    private List<Job> jobs;
    @Setter
    @Getter
    private LocalDateTime from;
    @Setter
    @Getter
    private LocalDateTime to;

    public void init(LocalDate date){

        this.from = date.atStartOfDay().minusDays(1);
        this.to = from.plusDays(3);
        this.jobIdMap = new HashMap<>();
        this.dbJobRowMap = jobDBLoader.loadJobRowMapFromDb(
                from, to, "0119030000"
        );
        createJobList();
    }

    private void createJobList() {

          this.jobs = new ArrayList<>();

        for (DbJobRow r : getDbJobRowMap().values()) {

            if(r.krc() == null) continue;
            Job job = createJobById(r.snpz().intValueExact());

            jobs.add(job);
        }
    }

    public Job createJobById(int snpz) {

        Job existing = jobIdMap.get(snpz);
        if (existing != null) {
            return existing;
        }

        DbJobRow row = dbJobRowMap.get(snpz);
        if (row == null) {
            throw new IllegalArgumentException("Unknown SNPZ=" + snpz);
        }

        Product product = loadDataService.getProducts().get(row.kmc());
        if (product == null) {
            throw new IllegalStateException("Unknown product KMC=" + row.kmc());
        }

        LocalDateTime startProductionDateTime =
                row.startProductionDateTime() != null
                        ? row.startProductionDateTime().toLocalDateTime()
                        : null;

        Job job = jobFactory.createJob(
                String.valueOf(row.snpz()), row.krc(), snpz,
                row.np(), jobFactory.nameCleaner(row.shortName()), product,
                row.mass(), row.quantity(), safe(row.duration()), from, from.plusHours(2), to,
                row.priority(), startProductionDateTime
        );

        jobIdMap.put(snpz, job);
        return job;
    }


    public List<DbJobRow> getDbJobRowList() {
        if (dbJobRowMap == null || dbJobRowMap.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(dbJobRowMap.values());
    }

    private int safe(Integer v) {
        return v != null ? v : 0;
    }

    private double safe(Double v) {
        return v != null ? v : 0.0;
    }
}


