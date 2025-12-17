package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.factory.JobFactory;
import org.acme.foodpackaging.persistence.db.JobDBLoader;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Getter
    private Map<Integer, DbJobRow> dbJobRowMap;
    @Getter
    private List<Job> jobs;
    @Getter
    private LocalDateTime from;
    @Getter
    private LocalDateTime to;

    public void init(LocalDate date){

        this.from = date.atStartOfDay().minusDays(1);
        this.to = from.plusDays(3);
        this.dbJobRowMap = jobDBLoader.loadJobRowMapFromDb(
                from, to, "0119030000"
        );
        createJobList();
    }

    private void createJobList() {

          this.jobs = new ArrayList<>();

        for (DbJobRow r : getDbJobRowMap().values()) {

            if(r.krc() == null) continue;

            Product product =
                    loadDataService.getProducts().get(r.kmc());

            if (product == null) {
                throw new IllegalStateException(
                        "Unknown product KMC=" + r.kmc());
            }

            LocalDateTime startProductionDateTime = r.startProductionDateTime() != null ? r.startProductionDateTime().toLocalDateTime() : null;

            Job job = jobFactory.createJob(
                    String.valueOf(r.snpz()), r.krc(), r.snpz().intValueExact(), r.np(),
                    jobFactory.nameCleaner(r.shortName()), product, r.mass(), r.quantity(), safe(r.duration()),
                    from, from.plusHours(2), to, r.priority(), startProductionDateTime
            );

            jobs.add(job);
        }
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


