package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.factory.JobFactory;
import org.acme.foodpackaging.persistence.db.JobDBLoader;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.getMaintenanceProduct;

@ApplicationScoped
public class JobRepository {

    @Inject
    JobFactory jobFactory;
    @Inject
    LoadDataService loadDataService;
    @Inject
    JobDBLoader jobDBLoader;

    @ConfigProperty(name = "ksk")
    String ksk;

    public Map<Integer, DbJobRow> getDbJobRowMap(LocalDate from, LocalDate to){
        return jobDBLoader.loadJobRowMapFromDb(
                from.atStartOfDay(), to.atStartOfDay(), ksk
        );
    }

    public Map<Long, DbMaintenanceRow> getDbMaintenanceRowMap(LocalDate from, LocalDate to){
        return jobDBLoader.loadMaintenanceRowMapFromDb(
                from.atStartOfDay(), to.atStartOfDay()
        );
    }

    public void initSolutionJobList(PackagingSchedule solution) {

          List<Job> jobs = new ArrayList<>();

        for (DbJobRow r : solution.getDbJobRowMap().values()) {

            if(r.lineId() == null) continue;
            Job job = createJobById(r.snpz().intValueExact(), false, solution);

            jobs.add(job);
        }

        for (DbMaintenanceRow rm : solution.getDbMaintenanceRowMap().values()) {

            if(rm.getLineId() == null) continue;
            Job job = createJobById((int) rm.getFId(), true, solution);

            jobs.add(job);
        }
        solution.setJobs(jobs);
    }

    public Job createJobById(int id, boolean serviceWork, PackagingSchedule solution) {

        Job job = new Job();

        if(serviceWork){

            DbMaintenanceRow row = solution.getDbMaintenanceRowMap().get(id);

            job = jobFactory.createJob(
                    String.valueOf(row.getFId()), row.getLineId(), row.getSnpz().intValueExact(),
                    -1, row.getShortName(), getMaintenanceProduct(), -1,
                    -1, safe(row.getDuration()), solution.getWorkCalendar().getMinStartDateTime(),
                    solution.getWorkCalendar().getIdealEndDateTime(), solution.getWorkCalendar().getMaxEndDateTime(),
                    0, getStartProductionDateTime(row.getStartProductionDateTime())
            );

            job.setMaintenance(true);
        }
        else {

            Job existing = solution.getJobIdMap().get(id);
            if (existing != null) {
                return existing;
            }

            DbJobRow row = solution.getDbJobRowMap().get(id);
            if (row == null) {
                throw new IllegalArgumentException("Unknown SNPZ=" + id);
            }

            Product product = loadDataService.getProducts().get(row.kmc());
            if (product == null) {
                throw new IllegalStateException("Unknown product KMC=" + row.kmc());
            }

            job = jobFactory.createJob(
                    String.valueOf(row.snpz()), row.lineId(), row.snpz().intValueExact(),
                    row.np(), jobFactory.nameCleaner(row.shortName()), product,
                    row.mass(), row.quantity(), safe(row.duration()), solution.getWorkCalendar().getMinStartDateTime(),
                    solution.getWorkCalendar().getIdealEndDateTime(), solution.getWorkCalendar().getMaxEndDateTime(),
                    row.priority(), getStartProductionDateTime(row.startProductionDateTime())
            );
            solution.getJobIdMap().put(row.snpz().intValueExact(), job);
        }
        return job;
    }

    public LocalDateTime getStartProductionDateTime(Timestamp startProductionDateTime){
        return startProductionDateTime != null
                ? startProductionDateTime.toLocalDateTime()
                : null;
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


