package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;

import java.time.Duration;
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

        List<Job> jobs = line.getJobs();
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        // факт по batch (глобально)
        Map<String, List<Job>> factBatches = jobs.stream()
                .filter(j -> j.getCameraStart() != null)
                .filter(j -> j.getCameraEnd() != null)
                .filter(j -> j.getIdBatch() != null)
                .collect(Collectors.groupingBy(Job::getIdBatch));

        List<MaintenanceRequest> requests = new ArrayList<>();

        int i = 0;

        while (i < jobs.size()) {

            Job current = jobs.get(i);

            if (current.isMaintenance() || current.getIdBatch() == null) {
                i++;
                continue;
            }

            String batchId = current.getIdBatch();
            int chainStartIndex = i;

            // строим непрерывную цепочку одинакового batch
            while (i < jobs.size()
                    && !jobs.get(i).isMaintenance()
                    && batchId.equals(jobs.get(i).getIdBatch())) {
                i++;
            }

            int chainEndIndex = i - 1;

            List<Job> factBatch = factBatches.get(batchId);
            if (factBatch == null || factBatch.isEmpty()) {
                continue; // факта нет — игнорируем
            }

            Job firstPlan = jobs.get(chainStartIndex);

            // защита от повторного добавления
            if (hasAlignMaintenanceForBatch(line, batchId)) {
                continue;
            }

            Job firstFact = factBatch.stream()
                    .min(Comparator.comparing(Job::getCameraStart))
                    .orElse(null);

            if (firstFact == null
                    || firstPlan.getStartProductionDateTime() == null) {
                continue;
            }

            long diffMinutes = ceilMinutes(
                    Duration.between(
                            firstPlan.getStartProductionDateTime(),
                            firstFact.getCameraStart()
                    )
            );

            if (diffMinutes <= 5) {
                continue;
            }

            MaintenanceRequest request = new MaintenanceRequest();
            request.setLineId(line.getId());
            request.setInsertIndex(chainStartIndex);
            request.setDurationMinutes((int) diffMinutes);
            request.setMaintenanceTypeId(8);
            request.setMaintenanceNote(
                    "Сдвиг batch " + batchId +
                            " по факту. PlanJob id=" + firstPlan.getId()
            );

            requests.add(request);
        }

        // вставка с конца
        requests.sort(Comparator.comparing(MaintenanceRequest::getInsertIndex).reversed());

        for (MaintenanceRequest request : requests) {
            maintenanceJob.addMaintenanceJob(schedule, request);
        }
    }

    private boolean hasAlignMaintenanceForBatch(Line line, String batchId) {
        return line.getJobs().stream()
                .anyMatch(j ->
                        j.isMaintenance()
                                && j.getMaintenanceTypeId() == 8
                                && j.getMaintenanceNote() != null
                                && j.getMaintenanceNote().contains("batch " + batchId)
                );
    }

}
