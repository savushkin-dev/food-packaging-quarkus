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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

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
        int acceptableDiff = 5;
        for (Job job : jobs) {
            Long factMinutes = calculateFactMinutes(job);
            if (factMinutes == null) {
                continue;
            }
            long planMinutes = calculatePlanMinutes(job);
            long diff = factMinutes - planMinutes;
    
            if (diff > acceptableDiff && !hasDeviationMaintenance(job)) {
                toInsert.add(new MaintenanceToInsert(job, diff));
            }
        }
        return toInsert;
    }

    private boolean hasDeviationMaintenance(Job job) {

        Job next = job.getNextJob();
        if (next == null) {
            return false;
        }

        return next.isMaintenance()
            && (next.getMaintenanceTypeId() == 7
                || next.getMaintenanceTypeId() == 8);
}

private boolean hasStartShiftMaintenance(Job job) {
    Job previous = job.getPreviousJob();
    if (previous == null) {
        return false;
    }

    return previous.isMaintenance()
            && previous.getMaintenanceTypeId() == 8;
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
        if (jobs.isEmpty()) {
            return;
        }

        // --- 1. Собираем продукты, которые есть по факту ---
        Map<String, List<Job>> factByProduct = jobs.stream()
                .filter(j -> j.getCameraStart() != null)
                .filter(j -> j.getCameraEnd() != null)
                .filter(j -> j.getProduct() != null)
                .collect(Collectors.groupingBy(j -> j.getProduct().getName()));

        if (factByProduct.isEmpty()) {
            return;
        }

        List<MaintenanceRequest> requests = new ArrayList<>();

        for (String product : factByProduct.keySet()) {

            // самая ранняя плановая задача этого продукта
            Job earliestPlanJob = jobs.stream()
                    .filter(j -> !j.isMaintenance())
                    .filter(j -> j.getProduct() != null)
                    .filter(j -> product.equals(j.getProduct().getName()))
                    .filter(j -> j.getCameraStart() != null)
                    .filter(j -> j.getCameraEnd() != null)
                    .filter(j -> j.getStartProductionDateTime() != null)
                    .filter(j -> j.getLine() != null
                            && line.getId().equals(j.getLine().getId()))
                    .min(Comparator.comparing(Job::getStartProductionDateTime))
                    .orElse(null);

            if (earliestPlanJob == null) {
                continue;
            }

            int index = jobs.indexOf(earliestPlanJob);
            if (index < 0) {
                continue;
            }

            Job earliestFactJob = factByProduct.get(product).stream()
                    .min(Comparator.comparing(Job::getCameraStart))
                    .orElse(null);

            if (earliestFactJob == null) {
                continue;
            }

            LocalDateTime planStart = earliestPlanJob.getStartProductionDateTime();
            LocalDateTime factStart = earliestFactJob.getCameraStart();

            if (!planStart.isBefore(factStart)) {
                continue;
            }

            long diffMinutes = ceilMinutes(
                    Duration.between(planStart, factStart)
            );

            if (diffMinutes <= 5) {
                continue;
            }

            // --- защита от дублей ---
            if (index > 0) {
                Job previous = jobs.get(index - 1);
                if (previous.isMaintenance()
                        && previous.getMaintenanceTypeId() != null
                        && previous.getMaintenanceTypeId() == 8) {
                    continue;
                }
            }

            MaintenanceRequest request = new MaintenanceRequest();
            request.setLineId(line.getId());
            request.setInsertIndex(index);
            request.setDurationMinutes((int) diffMinutes);
            request.setMaintenanceTypeId(8);
            request.setMaintenanceNote(
                    "Сдвиг продукта " + product +
                            ". PlanJob id=" + earliestPlanJob.getId()
            );

            requests.add(request);
        }

        // вставляем с конца
        requests.sort(Comparator.comparing(MaintenanceRequest::getInsertIndex).reversed());

        for (MaintenanceRequest request : requests) {
            maintenanceJob.addMaintenanceJob(schedule, request);
        }
    }

}
