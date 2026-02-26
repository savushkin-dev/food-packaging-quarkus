package org.acme.foodpackaging.scheduleoperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

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
        
            LocalDateTime startTime = request.getStartProductionDateTime();
        
            if (startTime != null) {
                maintenanceJob.setStartCleaningDateTime(startTime);
                maintenanceJob.setStartProductionDateTime(startTime);
            }
        
            lineJobs.add(insertIndex, maintenanceJob);
            insertedIndex = insertIndex;
        }

        fixLineJobs(line);
        fixPinnedJobs(line);

        schedule.getJobs().add(maintenanceJob);

        int packagingType = 7;
        int alignType = 8;

        if (!(typeKey == packagingType || typeKey == alignType)) {
            maybeAddExtraMaintenance(schedule, line, lineJobs, maintenanceJob, request, insertedIndex);
        }   

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
        if(jobToRemove.isMaintenance()) {
            schedule.getDeletedMaintenance().add(jobToRemove);
            jobToRemove.setFDel((short) 1);
            schedule.getJobs().remove(jobToRemove);

            lineJobs.remove(index);

            fixLineJobs(line);
            fixPinnedJobs(line);
        }
        return schedule;
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

            Duration required_duration = Duration.ofMinutes(CleaningDurationUtils.getLinesCleaning().get(line.getId()));
            LocalDateTime dailyCleaningStart = getDailyCleaningStart(line, required_duration);
            if(dailyCleaningStart== null || dailyCleaningStart.isAfter(line.getJobs().getLast().getEndDateTime())) continue;

            int minutesInt = Math.toIntExact(required_duration.toMinutes());
            createDailyCleaningJob(schedule, line, dailyCleaningStart, minutesInt);
        }
    }

    private LocalDateTime getDailyCleaningStart(
            Line line, Duration required_duration
    ) {

        LocalDateTime dailyCleaningStart= null;

        List<Job> lineJobs = line.getJobs();

        if (lineJobs == null || lineJobs.isEmpty()) {
            return dailyCleaningStart;
        }

        for(Job job : lineJobs.reversed()){
            if(job.isMaintenance() && job.getMaintenanceTypeId()==2
                    && job.getDuration().compareTo(required_duration)>=0){
               dailyCleaningStart = job.getEndDateTime().plusHours(24);
               break;
            }

            Duration cleaningDuration = Duration.between(job.getStartProductionDateTime(), job.getStartCleaningDateTime());

            if(cleaningDuration.compareTo(required_duration)>=0){
               dailyCleaningStart = job.getStartProductionDateTime().plusHours(24);
               break;
            }

        }
        return dailyCleaningStart;
    }

    private void createDailyCleaningJob(
            PackagingSchedule schedule,
            Line line,
            LocalDateTime startTime,
            int durationMinutes
    ) {
        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId(line.getId());
        request.setMaintenanceTypeId(2);
        request.setDurationMinutes(durationMinutes);
        request.setStartProductionDateTime(startTime);

        addMaintenanceJob(schedule, request);
    }

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
