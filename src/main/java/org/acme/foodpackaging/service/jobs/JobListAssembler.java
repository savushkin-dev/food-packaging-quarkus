package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.WorkCalendar;
import org.acme.foodpackaging.dto.oeepev.CleaningRow;
import org.acme.foodpackaging.dto.oeepev.DelayRow;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.findLineById;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobListAssembler {

    private final JobFactory jobFactory;
    private final JobRepository jobRepository;

    public JobAssemblyResult assemble(PackagingSchedule solution) {
        WorkCalendar calendar = solution.getWorkCalendar();
        JobSourceData data = JobSourceData.loadFor(jobRepository, calendar.getFromDate(), calendar.getToDate());
        LocalDateTime minStartDateTime = calendar.getMinStartDateTime();

        Map<Long, Job> allJobsById = HashMap.newHashMap(data.jobRows().size());
        List<Job> jobs = new ArrayList<>(data.jobRows().size() + data.maintenanceRows().size());

        initProductionJobs(solution, data.jobRows(), data.cleaningData(), jobs, allJobsById, minStartDateTime);
        initMaintenanceJobs(solution, data.maintenanceRows(), jobs, minStartDateTime);

        applyDelayDurations(jobs, data.delayDurations());
        applyCleaningDelayDurations(jobs, data.cleaningDelayDurations());

        return new JobAssemblyResult(jobs, allJobsById, new ArrayList<>(data.jobRows().values()));
    }

    private void initProductionJobs(PackagingSchedule solution, Map<Long, JobRow> jobRows,
                                    Map<Long, CleaningRow> cleaningIdMap, List<Job> jobs,
                                    Map<Long, Job> allJobsById, LocalDateTime minStartDateTime) {
        for (JobRow row : jobRows.values()) {
            Job job = jobFactory.createProductionJob(row, allJobsById);
            Line line = findLineById(solution, job.getLineId());
            if (line != null) {
                attachJobToLineIfNeeded(line, jobs.size(), solution.getLines().size());
                enrichProductionJob(job, row, cleaningIdMap, minStartDateTime);
                assignJobToLine(job, line, jobs);
            }
        }
    }

    private void initMaintenanceJobs(PackagingSchedule solution, List<MaintenanceRow> serviceData,
                                     List<Job> jobs, LocalDateTime minStartDateTime) {
        for (MaintenanceRow row : serviceData) {
            if (row.lineId() == null) continue;
            Job job = jobFactory.createMaintenanceJob(row, solution.getMaintenanceProduct());
            Line line = findLineById(solution, job.getLineId());
            if (line != null) {
                attachJobToLineIfNeeded(line, jobs.size(), solution.getLines().size());
                job.setLine(line);
                job.setMinStartTime(minStartDateTime);
                assignJobToLine(job, line, jobs);
            }
        }
    }

    private void enrichProductionJob(Job job, JobRow row, Map<Long, CleaningRow> cleaningIdMap,
                                     LocalDateTime minStartDateTime) {
        job.setDti(row.dti());
        job.setMinStartTime(minStartDateTime);
        try {
            CleaningRow cleaningRow = cleaningIdMap.get(Long.valueOf(job.getId()));
            if (cleaningRow != null) {
                job.setCleaningFId(cleaningRow.fId());
            }
        } catch (NumberFormatException ignored) {
            // job id не числовой — записи о мойке для него нет
        }
    }

    private void assignJobToLine(Job job, Line line, List<Job> jobs) {
        line.getJobs().add(job);
        jobs.add(job);
    }

    private void attachJobToLineIfNeeded(Line line, int size, int lineCount) {
        if (line.getJobs() == null) {
            line.setJobs(new ArrayList<>(size / lineCount));
        }
    }

    private void applyDelayDurations(List<Job> jobs, Map<Long, DelayRow> delayDurationMap) {
        applyDuration(jobs, delayDurationMap, (job, row) -> {
            job.setDelayFId(row.fId());
            job.setDelayDuration(Duration.ofMinutes(row.duration()));
            job.setDelayNote(row.note());
        });
    }

    private void applyCleaningDelayDurations(List<Job> jobs, Map<Long, DelayRow> cleaningDelayDurationMap) {
        applyDuration(jobs, cleaningDelayDurationMap, (job, row) -> {
            job.setCleaningDelay(Duration.ofMinutes(row.duration()));
            job.setCleaningDelayNote(row.note());
        });
    }

    private void applyDuration(List<Job> jobs, Map<Long, DelayRow> durationMap, BiConsumer<Job, DelayRow> applier) {
        for (Job job : jobs) {
            long jobId;
            try {
                jobId = Long.parseLong(job.getId());
            } catch (NumberFormatException e) {
                continue;
            }
            DelayRow row = durationMap.get(jobId);
            if (row != null) {
                applier.accept(job, row);
            }
        }
    }

    public record JobAssemblyResult(List<Job> jobs, Map<Long, Job> allJobsById, List<JobRow> jobRows) {}

    private record JobSourceData(
            List<MaintenanceRow> maintenanceRows,
            Map<Long, DelayRow> delayDurations,
            Map<Long, DelayRow> cleaningDelayDurations,
            Map<Long, CleaningRow> cleaningData,
            Map<Long, JobRow> jobRows
    ) {
        static JobSourceData loadFor(JobRepository repo, LocalDate from, LocalDate to) {
            return new JobSourceData(
                    repo.getMaintenanceData(from, to),
                    repo.loadDelayDurationRows(from, to),
                    repo.loadCleaningDelayDurationRows(from, to),
                    repo.getCleaningData(from, to),
                    repo.getJobRowMap(from, to)
            );
        }
    }
}
