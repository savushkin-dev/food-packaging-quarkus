package org.acme.foodpackaging.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.UnaryOperator;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.NextElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.acme.foodpackaging.dto.request.maintenance.MaintenanceRequest;
import org.acme.foodpackaging.dto.row.jobs.JobRow;
import org.acme.foodpackaging.dto.row.maintenance.MaintenanceRow;
import org.acme.foodpackaging.persistence.serializer.DurationMinutesSerializer;
import org.acme.foodpackaging.domain.value.CleaningResult;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;

@Getter
@Setter
@NoArgsConstructor
@PlanningEntity
public class Job {

    @PlanningId
    private String id;
    private Long cleaningFId;
    private Long cleaningDelayFId;
    private Long delayFId;
    private short fDel;
    private String idBatch;
    private String lineId;
    private String lineIdFact;
    private String name;
    private String maintenanceNote;

    private Long snpz;
    private int np;
    private int quantity;

    private double mass;

    private Product product;
    private Duration duration;

    @JsonSerialize(using = DurationMinutesSerializer.class)
    private Duration delayDuration;
    @JsonSerialize(using = DurationMinutesSerializer.class)
    private Duration cleaningDelay;
    private boolean maintenance;
    private boolean handPackaging;
    private Integer maintenanceTypeId;

    private LocalDateTime cameraStart;
    private LocalDateTime cameraEnd;
    private LocalDateTime dtv;
    private LocalDateTime dti;
    private LocalDateTime startProductionDateTimeFact;
    private LocalDateTime minStartTime;
    private LocalDateTime idealEndTime;
    private LocalDateTime maxEndTime;
    private LocalDateTime drawCleaningStart;
    private LocalDateTime drawCleaningEnd;
    private Integer emk;
    private String placeFactInfo;
    private String delayNote;
    private String cleaningDelayNote;
    private Integer placePlan;

    private int priority;

    @PlanningPin
    private boolean pinned;

    @InverseRelationShadowVariable(sourceVariableName = "jobs")
    private Line line;

    @JsonIgnore
    @PreviousElementShadowVariable(sourceVariableName = "jobs")
    private Job previousJob;

    @JsonIgnore
    @NextElementShadowVariable(sourceVariableName = "jobs")
    private Job nextJob;

    /**
     * Start is after cleanup.
     */
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime startCleaningDateTime;

    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime startProductionDateTime;

    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime endDateTime;

    /**
     * Creates a regular production job from a database row.
     *
     * @param row                      The database row containing job data
     * @param product                  The product for this job
     * @param startProductionDateTime  Planned start of production
     * @param nameCleaner              Optional function to clean up the job name; may be null
     */
    public Job(JobRow row, Product product, LocalDateTime startProductionDateTime,
               UnaryOperator<String> nameCleaner) {
        String jobName = row.shortName() != null ? row.shortName().trim() : "";
        if (nameCleaner != null) {
            jobName = nameCleaner.apply(jobName);
        }

        this.id = String.valueOf(row.snpz());
        this.lineId = row.lineId();
        this.name = jobName;
        this.snpz = row.snpz();
        this.np = row.np() != null ? row.np() : 0;
        this.quantity = row.quantity() != null ? row.quantity() : 0;
        this.priority = normalizePriority(row.priority() != null ? row.priority() : 0);
        this.mass = row.mass();
        this.product = product;
        this.duration = row.duration() != null ? Duration.ofMinutes(row.duration()) : Duration.ZERO;
        this.startProductionDateTime = startProductionDateTime;
        this.endDateTime = startProductionDateTime == null ? null
                : startProductionDateTime.plus(this.duration);
        this.emk = row.emk() != null ? row.emk() : 0;
        this.placePlan = row.placePlan() != null ? row.placePlan() : 0;
        this.handPackaging = row.isHandPackaging();
    }

    /**
     * Creates a regular production job from a database row.
     *
     * @param mRow     The database row containing job data
     * @param mName    Name by event type
     * @param mProduct The empty product for this job
     */
    public Job(MaintenanceRow mRow, String mName, Product mProduct) {
        this.id = String.valueOf(mRow.fId());
        this.name = mName;
        this.lineId = mRow.lineId();
        this.maintenanceNote = mRow.note();
        this.maintenanceTypeId = mRow.eventTypeId();
        this.duration = Duration.ofMinutes(mRow.duration());
        this.priority = 1;
        this.pinned = true;
        this.maintenance = true;
        this.product = mProduct;
        this.startProductionDateTime = mRow.startProductionDateTime();
        this.endDateTime = startProductionDateTime == null ? null
                : startProductionDateTime.plus(duration);
    }

    /**
     * Creates a job with time constraints (used for maintenance jobs with
     * scheduling constraints).
     * Public for backward compatibility with tests - prefer other factory methods
     * for production code.
     * This method is kept for test compatibility.
     */

    public Job(String id, String name, Product product, Duration duration,
               int priority, boolean pinned, LocalDateTime startProductionDateTime) {
        this.id = id;
        this.name = name;
        this.product = product;
        this.duration = duration;
        this.priority = normalizePriority(priority);
        this.pinned = pinned;
        this.startProductionDateTime = startProductionDateTime;
        this.endDateTime = startProductionDateTime == null ? null : startProductionDateTime.plus(duration);
    }

    /**
     * Приоритеты из источника данных приходят в диапазоне 0..N, где 0 означает
     * "приоритет не задан". Ноль преобразуется в 1, остальные значения умножаются
     * на 10, чтобы оставить зазор для ручной точной настройки очерёдности.
     */
    private static int normalizePriority(int rawPriority) {
        return rawPriority == 0 ? 1 : rawPriority * 10;
    }

    public Job(String id, String name, MaintenanceRequest request, Product mProduct) {
        this.id = id;
        this.name = name;
        this.maintenance = true;
        this.product = mProduct;
        this.maintenanceTypeId = request.maintenanceTypeId();
        this.maintenanceNote = request.maintenanceNote();
        this.duration = Duration.ofMinutes(request.durationMinutes());
    }

    public Job(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public boolean areEqualsPlanAndFactLines() {
        if (lineIdFact == null || line == null || line.getId() == null)
            return false;
        return Objects.equals(lineIdFact, line.getId());
    }

    public long getCleaningDurationPlan() {
        if (product == null || product.getCleaningDurations() == null || previousJob == null
                || previousJob.getProduct() == null
                || previousJob.getProduct().getCleaningDurations() == null)
            return 0;
        CleaningResult meta = product.getCleaningResults().get(previousJob.getProduct());
        return meta.isPLRLC()
                ? CleaningDurationUtils.getLinesCleaning().get(line.getId())
                : product.getCleaningDurations().get(previousJob.getProduct()).toMinutes();
    }

    public long getCleaningDurationFact() {
        if (cleaningDelay == null) {
            return getCleaningDurationPlan();
        }
        return getCleaningDurationPlan() + cleaningDelay.toMinutes();
    }

    @Override
    public String toString() {
        return id + "(" + (product != null ? product.getName() : "null") + ")";
    }

    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    public Duration getDuration() {
        if (isMaintenance())
            return duration;
        return calculateDuration(true);
    }

    public long getFactDuration() {
        if (cameraStart == null || cameraEnd == null || !cameraStart.isBefore(cameraEnd)) {
            return 0;
        }

        ZoneId zone = ZoneId.systemDefault();

        return Duration.between(
                cameraStart.atZone(zone),
                cameraEnd.atZone(zone)).toMinutes();
    }

    public Duration getPlanDuration() {
        if (isMaintenance())
            return duration;
        return calculateDuration(false);
    }

    private Duration calculateDuration(boolean useDelay) {

        Integer speed;
        if (isHandPackaging()) {
            speed = getHandPackagingSpeed();
        } else {
            speed = getSpeed();
        }

        if (speed == null || speed <= 0) {
            return Duration.ZERO;
        }

        final int IF_CHANGING_PACKAGING = 4;
        long minutes = (long) Math.ceil(quantity / (double) speed) + IF_CHANGING_PACKAGING;
        if (useDelay) {
            final long finalDelay = getDelayDuration() == null ? 0 : getDelayDuration().toMinutes();
            minutes += finalDelay;
        }
        return Duration.ofMinutes(minutes);
    }

    @JsonIgnore
    public Integer getSpeed() {
        if (line == null || product == null || product.getType() == null)
            return null;
        return SpeedCacheUtils.getSpeed(line.getId(), product.getType());
    }

    @JsonIgnore
    public Integer getHandPackagingSpeed() {
        if (line == null || product == null || product.getType() == null)
            return null;
        return SpeedCacheUtils.getHandPackagingSpeed(line.getId(), product.getType());
    }

    public LocalDateTime getPlanEndDateTime() {

        if (startProductionDateTime == null)
            return null;

        Duration planDuration = calculateDuration(false);
        return startProductionDateTime.plusMinutes(planDuration.toMinutes());
    }
    // ************************************************************************
    // Complex methods
    // ************************************************************************

    @SuppressWarnings("unused")
    public void updateStartCleaningDateTime() {
        if (getLine() == null) {
            if (getStartCleaningDateTime() != null) {
                setStartCleaningDateTime(null);
                setStartProductionDateTime(null);
                setEndDateTime(null);
            }
            return;
        }
        Job previous = getPreviousJob();
        LocalDateTime startCleaning = previous == null ? line.getStartDateTime() : previous.getEndDateTime();
        LocalDateTime startProduction = computeStartProduction(previous, startCleaning);
        setStartCleaningDateTime(startCleaning);
        setStartProductionDateTime(startProduction);
        setEndDateTime(startProduction == null ? null : startProduction.plus(getDuration()));
    }

    private LocalDateTime computeStartProduction(Job previous, LocalDateTime startCleaning) {
        if (previous == null || previous.isMaintenance() || startCleaning == null || getProduct() == null
                || previous.getProduct() == null) {
            return startCleaning;
        }
        try {
            CleaningResult meta = product.getCleaningResults().get(previous.getProduct());
            Duration cleanupDuration = meta.isPLRLC()
                    ? Duration.ofMinutes(CleaningDurationUtils.getLinesCleaning().get(line.getId()))
                    : product.getCleaningDurations().get(previous.getProduct());
            cleanupDuration = cleaningDelay == null ? cleanupDuration : cleanupDuration.plus(cleaningDelay);
            if (cleanupDuration.isNegative()) {
                cleanupDuration = Duration.ofMinutes(10);
            }
            return startCleaning.plus(cleanupDuration);
        } catch (IllegalArgumentException | NullPointerException e) {
            return startCleaning;
        }
    }
}