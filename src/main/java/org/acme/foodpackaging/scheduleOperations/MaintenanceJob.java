package org.acme.foodpackaging.scheduleOperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.MaintenanceRequestDTO;
import org.acme.foodpackaging.scheduleOperations.utils.ScheduleFixUtils;
import org.acme.foodpackaging.service.ScheduleLogService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MaintenanceJob {
    @Inject
    ScheduleLogService scheduleLogService;

    /**
     * Добавляет Maintenance Job на линию
     *
     * @param schedule расписание
     * @param request  параметры запроса
     * @return обновлённое расписание
     */
    public PackagingSchedule addMaintenanceJob(PackagingSchedule schedule,
                                               MaintenanceRequestDTO request) {

        Line line = schedule.getLines().stream()
                .filter(l -> l.getId().equals(request.getLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + request.getLineId()));

        List<Job> lineJobs = line.getJobs();

        Map<String, Map<String, Integer>> lineSpeeds =
                schedule.getJobs().isEmpty()
                        ? Map.of()
                        : schedule.getJobs().getFirst().getLineSpeeds();

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
                schedule.getWorkCalendar().getIdealEndDateTime(),
                schedule.getWorkCalendar().getMaxEndDateTime(),
                0,
                true,
                null,
                null
        );

        maintenanceJob.setLineSpeeds(lineSpeeds);
        maintenanceJob.setMaintenance(true);
        maintenanceJob.setLine(line);

        if (request.isEmptyLineMode()) {
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

        ScheduleFixUtils.fixLineJobs(line);
        ScheduleFixUtils.fixPinnedJobs(line);

        schedule.getJobs().add(maintenanceJob);

        LocalDateTime actualStart = maintenanceJob.getStartProductionDateTime();
        LocalDateTime actualEnd = maintenanceJob.getEndDateTime();

        return schedule;
    }
}
