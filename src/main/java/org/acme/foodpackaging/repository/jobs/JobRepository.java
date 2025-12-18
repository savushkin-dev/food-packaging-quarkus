package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
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

    public Map<Integer, DbJobRow> getDbJobRowMap(LocalDate date){
        LocalDateTime from = date.atStartOfDay().minusDays(1);
        LocalDateTime to = from.plusDays(3);
        return jobDBLoader.loadJobRowMapFromDb(
                from, to, "0119030000"
        );
    }

    public void initSolutionJobList(PackagingSchedule solution) {

          List<Job> jobs = new ArrayList<>();

        for (DbJobRow r : solution.getDbJobRowMap().values()) {

            if(r.krc() == null) continue;
            Job job = createJobById(r.snpz().intValueExact(), solution);

            jobs.add(job);
        }
        solution.setJobs(jobs);
    }

    public Job createJobById(int snpz, PackagingSchedule solution) {

        Job existing = solution.getJobIdMap().get(snpz);
        if (existing != null) {
            return existing;
        }

        DbJobRow row = solution.getDbJobRowMap().get(snpz);
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
                row.mass(), row.quantity(), safe(row.duration()), solution.getWorkCalendar().getMinStartDateTime(),
                solution.getWorkCalendar().getIdealEndDateTime(), solution.getWorkCalendar().getMaxEndDateTime(),
                row.priority(), startProductionDateTime
        );

        solution.getJobIdMap().put(snpz, job);
        return job;
    }

    public List<DbJobRow> getDbJobRowList(Map<Integer, DbJobRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(rows.values());
    }

    private int safe(Integer v) {
        return v != null ? v : 0;
    }

}


