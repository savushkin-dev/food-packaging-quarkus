package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class AlignSolutionService {

    private final MaintenanceJob maintenanceJob;

    public AlignSolutionService(MaintenanceJob maintenanceJob) {
        this.maintenanceJob = maintenanceJob;
    }

    public void alignByFactDuration(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            List<Job> jobs = line.getJobs();
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }
            List<MaintenanceToInsert> toInsert = collectMaintenanceToInsert(jobs);
            insertMaintenanceItems(schedule, line, toInsert);
        }
    }

    private List<MaintenanceToInsert> collectMaintenanceToInsert(List<Job> jobs) {
        List<MaintenanceToInsert> toInsert = new ArrayList<>();
        int acceptable_diff = 5;
        for (Job job : jobs) {
            Long factMinutes = calculateFactMinutes(job);
            if (factMinutes == null) {
                continue;
            }
            long planMinutes = calculatePlanMinutes(job);
            long diff = factMinutes - planMinutes;
    
            if (diff > acceptable_diff) {
                toInsert.add(new MaintenanceToInsert(job, diff));
            }
        }
        return toInsert;
    }

    private void insertMaintenanceItems(PackagingSchedule schedule, Line line,
                                       List<MaintenanceToInsert> toInsert) {
        for (int i = toInsert.size() - 1; i >= 0; i--) {
            MaintenanceToInsert item = toInsert.get(i);
            int index = line.getJobs().indexOf(item.job);
            if (index < 0) {
                continue;
            }
            MaintenanceRequest request = new MaintenanceRequest();
            request.setLineId(line.getId());
            request.setInsertIndex(index + 1);
            request.setDurationMinutes((int) item.diffMinutes);
            request.setMaintenanceTypeId(7);
            request.setMaintenanceNote(
                    "Отклонение факт > план. Job id=" + item.job.getId()
            );
            maintenanceJob.addMaintenanceJob(schedule, request);
        }
    }

    private long calculatePlanMinutes(Job job) {
        if (job.getStartProductionDateTime() == null
                || job.getEndDateTime() == null) {
            return 0;
        }

        return ceilMinutes(Duration.between(
                job.getStartProductionDateTime(),
                job.getEndDateTime()
        ));
    }

    private Long calculateFactMinutes(Job job) {
        if (job.getCameraStart() == null
                || job.getCameraEnd() == null) {
            return null;
        }

        return ceilMinutes(Duration.between(
                job.getCameraStart(),
                job.getCameraEnd()
        ));
    }

    private long ceilMinutes(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 0;
        }
        return (duration.toSeconds() + 59) / 60;
    }

    private record MaintenanceToInsert(Job job, long diffMinutes) {
    }

    public void alignLineStartByFact(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            alignLineStartByFactForLine(schedule, line);
        }
    }

    private void alignLineStartByFactForLine(PackagingSchedule schedule, Line line) {
        List<Job> jobs = line.getJobs() != null ? line.getJobs() : List.of();
        List<Job> factJobs = !jobs.isEmpty()
                ? jobs.stream()
                        .filter(j -> j.getCameraStart() != null)
                        .filter(j -> j.getCameraEnd() != null)
                        .filter(j -> j.getStartProductionDateTime() != null)
                        .toList()
                : List.of();
        Job earliestPlanJob = factJobs.isEmpty() ? null
                : factJobs.stream()
                        .min(Comparator.comparing(Job::getStartProductionDateTime))
                        .orElse(null);
        int index = earliestPlanJob != null ? jobs.indexOf(earliestPlanJob) : -1;
        if (factJobs.isEmpty() || index < 0) {
            return;
        }
        Job earliestFactJob = factJobs.stream()
                .min(Comparator.comparing(Job::getCameraStart))
                .orElse(null);
        LocalDateTime factStart = earliestFactJob.getCameraStart();
        Job previous = earliestPlanJob.getPreviousJob();
        LocalDateTime referenceTime = previous != null && previous.getEndDateTime() != null
                ? previous.getEndDateTime()
                : earliestPlanJob.getStartProductionDateTime();
        long diffMinutes = ceilMinutes(Duration.between(referenceTime, factStart));
        if (diffMinutes > 5) {
            MaintenanceRequest request = new MaintenanceRequest();
            request.setLineId(line.getId());
            request.setInsertIndex(index);
            request.setDurationMinutes((int) diffMinutes);
            request.setMaintenanceTypeId(8);
            request.setMaintenanceNote(
                    "Сдвиг старта линии по факту. PlanJob id=" + earliestPlanJob.getId()
            );
            maintenanceJob.addMaintenanceJob(schedule, request);
        }
    }
}
