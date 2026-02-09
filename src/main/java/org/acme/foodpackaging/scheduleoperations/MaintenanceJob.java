package org.acme.foodpackaging.scheduleoperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

import static io.micrometer.core.instrument.config.validate.PropertyValidator.getDuration;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

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

        if (lineJobs.isEmpty()) {

            line.setStartDateTime(request.getStartProductionDateTime());

            LocalDateTime startTime = request.getStartProductionDateTime();
            maintenanceJob.setStartCleaningDateTime(startTime);
            maintenanceJob.setStartProductionDateTime(startTime);

            lineJobs.add(maintenanceJob);
            insertedIndex = 0;

        } else {

            Integer insertIndex = request.getInsertIndex();

            if (insertIndex == null) {
                insertIndex = findInsertIndexByTime(
                        lineJobs,
                        request.getStartProductionDateTime()
                );
            }

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
            addDailyFullCleaningForLine(schedule, line);
        }
    }

    private void addDailyFullCleaningForLine(PackagingSchedule schedule, Line line) {

        List<Job> lineJobs = line.getJobs();
        if (lineJobs == null || lineJobs.isEmpty()) {
            return;
        }

        Integer cleaningDurationMinutes = getDailyCleaningDurationMinutes(line);
        if (cleaningDurationMinutes == null) {
            return;
        }

        CleaningAnchor anchor = findDailyCleaningAnchor(lineJobs);
        Job anchorJob = anchor.anchorJob();
        if (anchorJob == null || anchorJob.getEndDateTime() == null) {
            return;
        }

        LocalDateTime dailyCleaningTime =
                anchorJob.getEndDateTime().plusHours(24);

        Job targetJob = findJobCoveringTime(lineJobs, dailyCleaningTime);
        if (targetJob == null) {
            return;
        }
        Duration targetDuration = targetJob.getDuration();
        Duration newCleaningDuration = Duration.ofMinutes(cleaningDurationMinutes);

        if(targetJob.isMaintenance()){
                if(targetDuration.compareTo(newCleaningDuration)>0) {
                    return;
                }
        else {
            targetJob.setDuration(newCleaningDuration);
            return;
         }
        }

        targetJob.setPinned_cleaning_duration(cleaningDurationMinutes);
        targetJob.setPinned_cleaning(true);
    }

    private Job findJobCoveringTime(List<Job> jobs, LocalDateTime time) {
        for (Job job : jobs) {
            LocalDateTime start = job.getStartProductionDateTime();
            LocalDateTime end = job.getEndDateTime();

            if (start != null && end != null
                    && !time.isBefore(start)
                    && time.isBefore(end)) {
                return job;
            }
        }
        return null;
    }

    private CleaningAnchor findDailyCleaningAnchor(List<Job> jobs) {

        Job longestWashJob = null;
        Duration longestWashDuration = Duration.ZERO;

        Job longestMaintenanceJob = null;
        Duration longestMaintenanceDuration = Duration.ZERO;

        for (Job job : jobs) {

            Duration washDuration = calculateWashDuration(job);
            if (washDuration == null || washDuration.isZero() || washDuration.isNegative()) {
                continue;
            }

            if (washDuration.compareTo(longestWashDuration) >= 0) {
                longestWashDuration = washDuration;
                longestWashJob = job;
            }

            if (isMaintenanceType2(job)
                    && washDuration.compareTo(longestMaintenanceDuration) >= 0) {
                longestMaintenanceDuration = washDuration;
                longestMaintenanceJob = job;
            }
        }

        Job anchorJob = chooseLongerJob(
                longestWashJob, longestWashDuration,
                longestMaintenanceJob, longestMaintenanceDuration
        );

        return new CleaningAnchor(anchorJob, longestWashDuration, longestMaintenanceDuration);
    }

    private Job chooseLongerJob(
            Job washJob, Duration washDuration,
            Job maintenanceJob, Duration maintenanceDuration
    ) {
        if (maintenanceJob == null) {
            return washJob;
        }
        if (washJob == null) {
            return maintenanceJob;
        }
        return maintenanceDuration.compareTo(washDuration) >= 0
                ? maintenanceJob
                : washJob;
    }

    private Duration calculateWashDuration(Job job) {
        LocalDateTime cleaningStart = job.getStartCleaningDateTime();
        LocalDateTime productionStart = job.getStartProductionDateTime();

        if (cleaningStart == null || productionStart == null) {
            return null;
        }

        return Duration.between(cleaningStart, productionStart);
    }


    private boolean isMaintenanceType2(Job job) {
        return job.isMaintenance()
                && Integer.valueOf(2).equals(job.getMaintenanceTypeId());
    }

    private Integer getDailyCleaningDurationMinutes(Line line) {
        Map<String, Integer> cleanings = CleaningDurationUtils.getLinesCleaning();
        return cleanings != null ? cleanings.get(line.getId()) : null;
    }

    private record CleaningAnchor(
            Job anchorJob,
            Duration longestWashDuration,
            Duration longestMaintenanceDuration
    ) {}

    private int findInsertIndexByTime(
            List<Job> jobs,
            LocalDateTime insertTime
    ) {
        for (int i = 0; i < jobs.size(); i++) {
            Job j = jobs.get(i);

            LocalDateTime jStart = j.getStartProductionDateTime();
            if (jStart != null && insertTime.isBefore(jStart)) {
                return i;
            }
        }
        return jobs.size();
    }
}
