package org.acme.foodpackaging.scheduleOperations;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.dto.MaintenanceRequestDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class MaintenanceJob {
    /**
     * Добавляет Maintenance Job на линию
     *
     * @param schedule расписание
     * @param request  параметры запроса
     * @return обновлённое расписание
     */
    public PackagingSchedule addMaintenanceJob(PackagingSchedule schedule,
                                               MaintenanceRequestDTO request) {

        Line line = findLineById(schedule, request.getLineId());

        List<Job> lineJobs = line.getJobs();

        Product maintenanceProduct = schedule.getProducts().stream()
                .filter(p -> "MAINTENANCE".equalsIgnoreCase(p.getId()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Maintenance product with id='MAINTENANCE' not found")
                );

        Job maintenanceJob = new Job(
                "MAINTENANCE-" + UUID.randomUUID(),
                request.getName(),
                maintenanceProduct,
                Duration.ofMinutes(request.getDurationMinutes()),
                schedule.getWorkCalendar().getMinStartDateTime(),
                null, null, 0, true, null, null
        );

        maintenanceJob.setMaintenance(true);
        maintenanceJob.setLine(line);

        if (request.isEmptyLineMode()) {
            line.setStartDateTime(request.getStartProductionDateTime());
            LocalDateTime startTime = request.getStartProductionDateTime();
            maintenanceJob.setStartCleaningDateTime(startTime);
            maintenanceJob.setStartProductionDateTime(startTime);
            lineJobs.add(maintenanceJob);
        } else {
            int insertIndex = request.getInsertIndex();
            if (insertIndex < 0 || insertIndex > lineJobs.size()) {
                throw new IllegalArgumentException("Invalid insertIndex: " + insertIndex);
            }
            lineJobs.add(insertIndex, maintenanceJob);
        }

        fixLineJobs(line);
        fixPinnedJobs(line);

        schedule.getJobs().add(maintenanceJob);

        return schedule;
    }

    public static Product getMaintenanceProduct() {
       return new Product("Maintenance Product", "MAINTENANCE", "", "", "", "", "");
    }

    public PackagingSchedule removeMaintenanceJob(PackagingSchedule schedule,
                                                  MaintenanceRequestDTO request) {

        Line line = findLineById(schedule, request.getLineId());

        List<Job> lineJobs = line.getJobs();
        int index = request.getRemoveIndex();

        if (index < 0 || index >= lineJobs.size()) {
            throw new IllegalArgumentException("Invalid insertIndex: " + index);
        }

        Job jobToRemove = lineJobs.get(index);

        lineJobs.remove(index);
        schedule.getJobs().remove(jobToRemove);
        markDeletedByFId(jobToRemove.getFId(), schedule.getDbMaintenanceRowMap());

        fixLineJobs(line);
        fixPinnedJobs(line);

        return schedule;

    }

    public void markDeletedByFId(
            Long fId,
            Map<Long, DbMaintenanceRow> jobs
    ) {

        if (fId == null || jobs == null || jobs.isEmpty()) {
            return;
        }

        jobs.values().stream()
                .filter(Objects::nonNull)
                .filter(job -> job.getFId() == fId)
                .forEach(job -> job.setFDel((short) 1));
    }

    public PackagingSchedule updateDuration(PackagingSchedule schedule, MaintenanceRequestDTO request) {

        Line line = findLineById(schedule, request.getLineId());

        List<Job> jobs = line.getJobs();

        int index = request.getUpdateIndex();
        if (index < 0 || index >= jobs.size()) {
            throw new IllegalArgumentException("Invalid insertIndex: " + index);
        }

        Job job = jobs.get(index);

        job.setDuration(Duration.ofMinutes(request.getDurationMinutes()));

        fixLineJobs(line);
        fixPinnedJobs(line);

        return schedule;
    }
}
