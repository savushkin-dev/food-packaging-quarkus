package org.acme.foodpackaging.scheduleOperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class MaintenanceJob {
    /**
     * Добавляет Maintenance Job на линию
     *
     */

    private final LoadDataService loadDataService;

    @Inject
    public MaintenanceJob(LoadDataService loadDataService) {
        this.loadDataService = loadDataService;
    }

    public PackagingSchedule addMaintenanceJob(PackagingSchedule schedule,
                                               MaintenanceRequest request) {

        Line line = findLineById(schedule, request.getLineId());
        int typeKey = request.getMaintenanceTypeId() != null ? request.getMaintenanceTypeId(): 1;
        List<Job> lineJobs = line.getJobs();

        ConcurrentMap<Integer, String> maintenanceTypes =
                loadDataService != null ? loadDataService.getMaintenanceTypes() : null;
        String maintenanceTypeName = maintenanceTypes != null
                ? maintenanceTypes.getOrDefault(typeKey, "Обслуживание")
                : "Обслуживание";

        Job maintenanceJob = Job.createMaintenanceJob(
                "MAINTENANCE-" + UUID.randomUUID(), null,
                request.getMaintenanceTypeId(),
                maintenanceTypeName,
                request.getMaintenanceNote(),
                schedule.getMaintenanceProduct(),
                request.getDurationMinutes()
        );
        maintenanceJob.setLine(line);
        maintenanceJob.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());

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

    public static Product createMaintenanceProduct() {
       return new Product("Maintenance Product", "MAINTENANCE", "", "", "", "", "");
    }

    public PackagingSchedule removeMaintenanceJob(PackagingSchedule schedule,
                                                  MaintenanceRequest request) {

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
                .filter(job -> Objects.equals(job.getFId(), fId))
                .forEach(job -> job.setFDel((short) 1));
    }

    public PackagingSchedule updateDuration(PackagingSchedule schedule, MaintenanceRequest request) {

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

    public PackagingSchedule updateMaintenanceType(PackagingSchedule schedule, MaintenanceRequest request) {

        Line line = findLineById(schedule, request.getLineId());

        List<Job> jobs = line.getJobs();

        int index = request.getUpdateIndex();
        if (index < 0 || index >= jobs.size()) {
            throw new IllegalArgumentException("Invalid insertIndex: " + index);
        }

        Job job = jobs.get(index);
        job.setMaintenanceTypeId(request.getMaintenanceTypeId());
        job.setName(loadDataService.getMaintenanceTypes().get(job.getMaintenanceTypeId()));

        if(request.getDurationMinutes()!=null){
            job.setDuration(Duration.ofMinutes(request.getDurationMinutes()));
        }

        if(request.getMaintenanceNote()!=null)
        {
            job.setMaintenanceNote(request.getMaintenanceNote());
        }

        fixLineJobs(line);
        fixPinnedJobs(line);

        return schedule;
    }
}
