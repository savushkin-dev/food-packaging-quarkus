package org.acme.foodpackaging.scheduleOperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleOperations.utils.CleaningDurationUtils;

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

        int insertedIndex;
        if (request.isEmptyLineMode()) {
            line.setStartDateTime(request.getStartProductionDateTime());
            LocalDateTime startTime = request.getStartProductionDateTime();
            maintenanceJob.setStartCleaningDateTime(startTime);
            maintenanceJob.setStartProductionDateTime(startTime);
            lineJobs.add(maintenanceJob);
            insertedIndex = lineJobs.size() - 1;
        } else {
            int insertIndex = request.getInsertIndex();
            if (insertIndex < 0 || insertIndex > lineJobs.size()) {
                throw new IllegalArgumentException("Invalid insertIndex: " + insertIndex);
            }
            lineJobs.add(insertIndex, maintenanceJob);
            insertedIndex = insertIndex;
        }

        fixLineJobs(line);
        fixPinnedJobs(line);

        schedule.getJobs().add(maintenanceJob);

        maybeAddExtraMaintenance(schedule, line, lineJobs, maintenanceJob, request, insertedIndex);

        return schedule;
    }

    private void maybeAddExtraMaintenance(PackagingSchedule schedule,
                                          Line line,
                                          List<Job> lineJobs,
                                          Job primaryJob,
                                          MaintenanceRequest request,
                                          int insertedIndex) {
        Integer reqMinutes = request.getDurationMinutes();
        if (reqMinutes == null || reqMinutes < 6 * 60) return;

        Map<String, Integer> cleanings = CleaningDurationUtils.getLinesCleaning();
        if (cleanings == null) return;
        Integer extraMinutes = cleanings.get(request.getLineId());
        if (extraMinutes == null || extraMinutes <= 0) return;

        Job extraJob = Job.createMaintenanceJob(
                "MAINTENANCE-" + UUID.randomUUID(),
                null,
                2,
                "Мойка",
                "Auto extra maintenance",
                schedule.getMaintenanceProduct(),
                extraMinutes
        );
        extraJob.setLine(line);
        extraJob.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
        if (request.isEmptyLineMode()) {
            extraJob.setStartCleaningDateTime(primaryJob.getStartCleaningDateTime());
            extraJob.setStartProductionDateTime(primaryJob.getStartProductionDateTime());
        }
        int idx = Math.min(insertedIndex + 1, lineJobs.size());
        lineJobs.add(idx, extraJob);
        schedule.getJobs().add(extraJob);
        fixLineJobs(line);
        fixPinnedJobs(line);
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

    public void addDailyFullCleaning(PackagingSchedule schedule) {

        for (Line line : schedule.getLines()) {

            List<Job> jobs = line.getJobs();
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }

            Job longestJob = null;
            Duration longestDuration = Duration.ZERO;

            Job longestMaintenance2 = null;
            Duration longestMaintenance2Duration = Duration.ZERO;

            for (Job job : jobs) {

                LocalDateTime sc = job.getStartCleaningDateTime();
                LocalDateTime sp = job.getStartProductionDateTime();

                if (sc != null && sp != null) {
                    Duration d = Duration.between(sc, sp);
                    if (!d.isNegative() && !d.isZero()) {

                        if (d.compareTo(longestDuration) > 0) {
                            longestDuration = d;
                            longestJob = job;
                        }

                        if (job.isMaintenance()
                                && Integer.valueOf(2).equals(job.getMaintenanceTypeId())
                                && d.compareTo(longestMaintenance2Duration) > 0) {

                            longestMaintenance2Duration = d;
                            longestMaintenance2 = job;
                        }
                    }
                }
            }

            if (longestJob == null && longestMaintenance2 == null) {

                Job lastJob = jobs.getLast();
                LocalDateTime insertTime = lastJob.getEndDateTime();

                MaintenanceRequest request = new MaintenanceRequest();
                request.setLineId(line.getId());
                request.setMaintenanceTypeId(2);
                request.setDurationMinutes(
                        CleaningDurationUtils.getLinesCleaning().get(line.getId())
                );
                request.setStartProductionDateTime(insertTime);
                request.setInsertIndex(jobs.size());

                addMaintenanceJob(schedule, request);
                continue;
            }

            boolean useMaintenance =
                    longestMaintenance2 != null
                            && longestMaintenance2Duration.compareTo(longestDuration) >= 0;

            Job baseJob = useMaintenance ? longestMaintenance2 : longestJob;

            LocalDateTime baseTime;
            if (useMaintenance) {
                baseTime = baseJob.getStartProductionDateTime();
            } else {
                assert baseJob != null;
                baseTime = baseJob.getStartCleaningDateTime();
            }

            LocalDateTime insertTime = baseTime.plusHours(24);

            MaintenanceRequest request = new MaintenanceRequest();
            LocalDateTime lineEndDateTime = line.getJobs().getLast().getEndDateTime().plusHours(24);
            line.setMaxEndTime(lineEndDateTime);

            request.setLineId(line.getId());
            request.setMaintenanceTypeId(2);
            request.setDurationMinutes(
                    CleaningDurationUtils.getLinesCleaning().get(line.getId())
            );
            request.setStartProductionDateTime(insertTime);

            addMaintenanceJob(schedule, request);
        }
    }
}
