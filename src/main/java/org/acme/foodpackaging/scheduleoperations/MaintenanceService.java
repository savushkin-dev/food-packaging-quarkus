package org.acme.foodpackaging.scheduleoperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.request.maintenance.AddMaintenanceRequest;
import org.acme.foodpackaging.dto.request.maintenance.UpdateMaintenanceRequest;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MaintenanceService {

    private final LoadDataService loadDataService;

    /**
     * Добавляет Maintenance Job на линию (вставка в существующее расписание либо
     * старт пустой линии)
     */

    public PackagingSchedule addMaintenanceJob(PackagingSchedule schedule, AddMaintenanceRequest request) {

        Line line = findLineById(schedule, request.lineId());
        int typeKey = request.maintenanceTypeId() != null ? request.maintenanceTypeId() : 1;

        String maintenanceTypeName = resolveMaintenanceTypeName(typeKey);
        Job maintenanceJob = buildMaintenanceJob(schedule, request, line, maintenanceTypeName);

        int insertedIndex = insertMaintenanceJob(line, maintenanceJob, request);

        fixLineJobs(line);
        fixPinnedJobs(line);

        schedule.getJobs().add(maintenanceJob);

        Integer alignExtraCleaning = request.alignExtraCleaning();
        if (alignExtraCleaning != null) {
            maybeAddExtraMaintenance(schedule, line, request, alignExtraCleaning, insertedIndex);
        }

        ExtraMaintenance extraMaintenance = getExtraMaintenanceData(request);
        if (extraMaintenance.isNeed()) {
            maybeAddExtraMaintenance(schedule, line, request, extraMaintenance.extraMinutes(), insertedIndex);
        }

        return schedule;
    }

    /**
     * Обновляет существующую Maintenance Job — тип, заметка и/или длительность,
     * в зависимости от того, что передано в запросе
     */
    public PackagingSchedule updateMaintenanceJob(PackagingSchedule schedule, UpdateMaintenanceRequest request) {

        Line line = findLineById(schedule, request.lineId());
        List<Job> jobs = line.getJobs();

        int index = request.updateIndex();
        if (index < 0 || index >= jobs.size()) {
            throw new IllegalArgumentException("Invalid updateIndex: " + index);
        }

        Job job = jobs.get(index);

        if (request.maintenanceTypeId() != null) {
            job.setMaintenanceTypeId(request.maintenanceTypeId());
            job.setName(loadDataService.getMaintenanceTypes().get(job.getMaintenanceTypeId()));
        }
        if (request.maintenanceNote() != null) {
            job.setMaintenanceNote(request.maintenanceNote());
        }
        if (request.durationMinutes() != null) {
            job.setDuration(Duration.ofMinutes(request.durationMinutes()));
        }

        fixLineJobs(line);
        fixPinnedJobs(line);

        return schedule;
    }

    /**
     * Удаляет Maintenance Job с линии
     */

    public PackagingSchedule removeMaintenanceJob(PackagingSchedule schedule, String lineId, int removeIndex) {

        Line line = findLineById(schedule, lineId);
        List<Job> lineJobs = line.getJobs();

        if (removeIndex < 0 || removeIndex >= lineJobs.size()) {
            throw new IllegalArgumentException("Invalid removeIndex: " + removeIndex);
        }

        Job jobToRemove = lineJobs.get(removeIndex);
        if (jobToRemove.isMaintenance()) {
            List<Job> deletedMaintenance = schedule.getDeletedMaintenance();
            deletedMaintenance.add(jobToRemove);
            schedule.setDeletedMaintenance(deletedMaintenance);

            jobToRemove.setFDel((short) 1);
            schedule.getJobs().remove(jobToRemove);

            lineJobs.remove(removeIndex);

            fixLineJobs(line);
            fixPinnedJobs(line);
        }
        return schedule;
    }

    public void addDailyFullCleaning(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {

            Duration requiredDuration = Duration.ofMinutes(CleaningDurationUtils.getLinesCleaning().get(line.getId()));
            long requiredMinutes = requiredDuration.toMinutes();
            LocalDateTime dailyCleaningStart = getDailyCleaningStart(line, requiredMinutes);
            if (dailyCleaningStart == null || dailyCleaningStart.isAfter(line.getJobs().getLast().getEndDateTime())) {
                continue;
            }

            int minutesInt = Math.toIntExact(requiredDuration.toMinutes());
            createDailyCleaningJob(schedule, line, dailyCleaningStart, minutesInt);
            line.setMaxEndTime(line.getJobs().getLast().getEndDateTime().plusHours(20));
        }
    }

    private void createDailyCleaningJob(PackagingSchedule schedule, Line line,
            LocalDateTime startTime, int durationMinutes) {

        AddMaintenanceRequest request = new AddMaintenanceRequest(
                line.getId(),
                null,
                2,
                durationMinutes,
                null,
                null,
                startTime);

        addMaintenanceJob(schedule, request);
    }

    // ---- вспомогательные методы (без изменений в логике, только сигнатуры под
    // новые DTO) ----

    private ExtraMaintenance getExtraMaintenanceData(AddMaintenanceRequest request) {
        int packagingType = 7;
        int alignType = 8;
        int maintenanceType = request.maintenanceTypeId();

        if (isMoreSixHours(request.durationMinutes()) && maintenanceType != alignType
                && maintenanceType != packagingType) {
            Map<String, Integer> cleanings = CleaningDurationUtils.getLinesCleaning();
            if (cleanings != null) {
                Integer extraMinutes = cleanings.get(request.lineId());

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

    private record ExtraMaintenance(boolean isNeed, Integer extraMinutes) {
    }

    private String resolveMaintenanceTypeName(int typeKey) {
        ConcurrentMap<Integer, String> maintenanceTypes = loadDataService != null
                ? loadDataService.getMaintenanceTypes()
                : null;
        return maintenanceTypes != null
                ? maintenanceTypes.getOrDefault(typeKey, "Обслуживание")
                : "Обслуживание";
    }

    private Job buildMaintenanceJob(PackagingSchedule schedule, AddMaintenanceRequest request,
            Line line, String maintenanceTypeName) {

        Job maintenanceJob = new Job("MAINTENANCE-" + UUID.randomUUID(),
                maintenanceTypeName, request, schedule.getMaintenanceProduct());
        maintenanceJob.setLine(line);
        maintenanceJob.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
        return maintenanceJob;
    }

    private int insertMaintenanceJob(Line line, Job maintenanceJob, AddMaintenanceRequest request) {
        List<Job> lineJobs = line.getJobs();
        LocalDateTime startTime = request.startProductionDateTime();

        if (lineJobs.isEmpty()) {
            line.setStartDateTime(startTime);
            maintenanceJob.setStartCleaningDateTime(startTime);
            maintenanceJob.setStartProductionDateTime(startTime);
            lineJobs.add(maintenanceJob);
            return 0;
        }

        Integer insertIndex = request.insertIndex();
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

    private void maybeAddExtraMaintenance(PackagingSchedule schedule, Line line, AddMaintenanceRequest request,
            int extraMinutes, int insertedIndex) {

        Job extraJob = createExtraCleaning(schedule.getMaintenanceProduct(), extraMinutes);

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

    private Job createExtraCleaning(Product maintenanceProduct, int duration) {
        Job extraJob = new Job("MAINTENANCE-" + UUID.randomUUID(), "Мойка");
        extraJob.setMaintenance(true);
        extraJob.setMaintenanceTypeId(2);
        extraJob.setMaintenanceNote("Auto extra maintenance");
        extraJob.setDuration(Duration.ofMinutes(duration));
        extraJob.setProduct(maintenanceProduct);

        return extraJob;
    }

    private LocalDateTime getDailyCleaningStart(Line line, long requiredMinutes) {

        LocalDateTime dailyCleaningStart = null;
        List<Job> lineJobs = line.getJobs();

        if (lineJobs == null || lineJobs.isEmpty()) {
            return dailyCleaningStart;
        }

        for (Job job : lineJobs.reversed()) {
            if (job.isMaintenance() && job.getMaintenanceTypeId() == 2
                    && job.getDuration().toMinutes() >= requiredMinutes) {
                dailyCleaningStart = job.getStartProductionDateTime().plusHours(24);
            } else {
                Duration cleaningDuration = Duration.between(
                        job.getStartCleaningDateTime().atZone(ZoneId.systemDefault()),
                        job.getStartProductionDateTime().atZone(ZoneId.systemDefault()));
                long cleaningMinutes = cleaningDuration.toMinutes();
                if (cleaningMinutes >= requiredMinutes) {
                    dailyCleaningStart = job.getStartCleaningDateTime().plusHours(24);
                }
            }
            if (dailyCleaningStart != null) {
                break;
            }
        }
        return dailyCleaningStart;
    }

    private int findInsertIndexByTime(List<Job> jobs, LocalDateTime insertTime) {
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
