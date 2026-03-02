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
        int typeKey = request.getMaintenanceTypeId() != null ? request.getMaintenanceTypeId() : 1;

        String maintenanceTypeName = resolveMaintenanceTypeName(typeKey);
        Job maintenanceJob = buildMaintenanceJob(schedule, request, line, maintenanceTypeName);

        int insertedIndex = insertMaintenanceJob(line, maintenanceJob, request);

        fixLineJobs(line);
        fixPinnedJobs(line);

        schedule.getJobs().add(maintenanceJob);
        Integer alignExtraCleaning = request.getAlignExtraCleaning();
        if(alignExtraCleaning!=null){
            maybeAddExtraMaintenance(schedule, line, request, alignExtraCleaning, insertedIndex);
        }

        ExtraMaintenance extraMaintenance = getExtraMaintenanceData(request);

         if(extraMaintenance.isNeed){
             maybeAddExtraMaintenance(schedule, line, request, extraMaintenance.extraMinutes, insertedIndex);
         }

        return schedule;
    }

    private ExtraMaintenance getExtraMaintenanceData(MaintenanceRequest request){
        int packagingType = 7;
        int alignType = 8;
        int maintenanceType = request.getMaintenanceTypeId();

        if(isMoreSixHours(request.getDurationMinutes()) && maintenanceType!=alignType && maintenanceType!=packagingType) {
            Map<String, Integer> cleanings = CleaningDurationUtils.getLinesCleaning();
            if (cleanings != null) {
                Integer extraMinutes = cleanings.get(request.getLineId());

                if (!(extraMinutes == null || extraMinutes <= 0)) {
                    return new ExtraMaintenance(true, extraMinutes);
                }
            }
        }
        return new ExtraMaintenance(false, null);
    }

    private boolean isMoreSixHours(Integer reqMinutes) {
        return reqMinutes != null && reqMinutes >= 6 * 60;
    }

    private record ExtraMaintenance( boolean isNeed, Integer extraMinutes ){}

    private String resolveMaintenanceTypeName(int typeKey) {
        ConcurrentMap<Integer, String> maintenanceTypes =
                loadDataService != null ? loadDataService.getMaintenanceTypes() : null;
        return maintenanceTypes != null
                ? maintenanceTypes.getOrDefault(typeKey, "Обслуживание")
                : "Обслуживание";
    }

    private Job buildMaintenanceJob(PackagingSchedule schedule,
                                    MaintenanceRequest request,
                                    Line line,
                                    String maintenanceTypeName) {
        Job maintenanceJob = Job.createMaintenanceJob(
                "MAINTENANCE-" + UUID.randomUUID(),
                null,
                request.getMaintenanceTypeId(),
                maintenanceTypeName,
                request.getMaintenanceNote(),
                schedule.getMaintenanceProduct(),
                request.getDurationMinutes()
        );
        maintenanceJob.setLine(line);
        maintenanceJob.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
        return maintenanceJob;
    }

    private int insertMaintenanceJob(Line line,
                                     Job maintenanceJob,
                                     MaintenanceRequest request) {
        List<Job> lineJobs = line.getJobs();
        LocalDateTime startTime = request.getStartProductionDateTime();

        if (lineJobs.isEmpty()) {
            line.setStartDateTime(startTime);
            maintenanceJob.setStartCleaningDateTime(startTime);
            maintenanceJob.setStartProductionDateTime(startTime);
            lineJobs.add(maintenanceJob);
            return 0;
        }

        Integer insertIndex = request.getInsertIndex();
        if (insertIndex == null) {
            insertIndex = findInsertIndexByTime(lineJobs, startTime);
        }

        if (insertIndex < 0 || insertIndex > lineJobs.size()) {
            throw new IllegalArgumentException("Invalid insertIndex: " + insertIndex);
        }

        if (startTime != null) {
            maintenanceJob.setStartCleaningDateTime(startTime);
            maintenanceJob.setStartProductionDateTime(startTime);
        }

        lineJobs.add(insertIndex, maintenanceJob);
        return insertIndex;
    }

    private void maybeAddExtraMaintenance(PackagingSchedule schedule,
                                          Line line, MaintenanceRequest request,
                                          int extraMinutes, int insertedIndex) {

        Job extraJob = createExtraCleaning(
                schedule.getMaintenanceProduct(), extraMinutes
        );

        extraJob.setLine(line);
        extraJob.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
        if (request.isEmptyLineMode()) {
            Job primaryJob = line.getJobs().get(insertedIndex);
            extraJob.setStartCleaningDateTime(primaryJob.getStartCleaningDateTime());
            extraJob.setStartProductionDateTime(primaryJob.getStartProductionDateTime());
        }
        int idx = Math.min(insertedIndex + 1, line.getJobs().size());
        line.getJobs().add(idx, extraJob);
        schedule.getJobs().add(extraJob);
        fixLineJobs(line);
        fixPinnedJobs(line);
    }

    public static Product createMaintenanceProduct() {
       return new Product("Maintenance Product", "MAINTENANCE", "", "", "", "", "");
    }

    private Job createExtraCleaning(Product maintenanceProduct, int duration){
        return Job.createMaintenanceJob(
                "MAINTENANCE-" + UUID.randomUUID(),
                null,
                2,
                "Мойка",
                "Auto extra maintenance",
                maintenanceProduct,
                duration
        );
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

            Duration requiredDuration = Duration.ofMinutes(CleaningDurationUtils.getLinesCleaning().get(line.getId()));
            long requiredMinutes = requiredDuration.toMinutes();
            LocalDateTime dailyCleaningStart = getDailyCleaningStart(line, requiredMinutes);
            if(dailyCleaningStart== null || dailyCleaningStart.isAfter(line.getJobs().getLast().getEndDateTime())) continue;

            int minutesInt = Math.toIntExact(requiredDuration.toMinutes());
            createDailyCleaningJob(schedule, line, dailyCleaningStart, minutesInt);
            line.setMaxEndTime(line.getJobs().getLast().getEndDateTime().plusHours(20));
        }
    }

    private LocalDateTime getDailyCleaningStart(
            Line line, long requiredMinutes
    ) {

        LocalDateTime dailyCleaningStart= null;

        List<Job> lineJobs = line.getJobs();

        if (lineJobs == null || lineJobs.isEmpty()) {
            return dailyCleaningStart;
        }

        for (Job job : lineJobs.reversed()) {
            if (job.isMaintenance() && job.getMaintenanceTypeId() == 2
                    && job.getDuration().toMinutes()>=requiredMinutes) {
                dailyCleaningStart = job.getStartProductionDateTime().plusHours(24);
            } else {
                Duration cleaningDuration = Duration.between( job.getStartCleaningDateTime(), job.getStartProductionDateTime());
                long cleaningMinutes = cleaningDuration.toMinutes();
                if (cleaningMinutes>=requiredMinutes) {
                    dailyCleaningStart = job.getStartCleaningDateTime().plusHours(24);
                }
            }
            if (dailyCleaningStart != null) {
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
