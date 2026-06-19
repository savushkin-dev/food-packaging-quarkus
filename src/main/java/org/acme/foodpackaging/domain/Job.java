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
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.persistence.serializer.DurationMinutesSerializer;
import org.acme.foodpackaging.record.CleaningResult;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.ProductionJobParams;
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

    @JsonIgnore
    private SpeedCacheUtils speedCache;
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
     * Constructor for regular production jobs.
     * Package-private - use factory methods for public API.
     */
    private Job(ProductionJobParams params) {
        this.id = params.id();
        this.lineId = params.lineId();
        this.name = params.name();
        this.snpz = params.snpz();
        this.np = params.np();
        this.quantity = params.quantity();
        this.priority = params.priority() == 0 ? 1 : params.priority() * 10;
        this.mass = params.mass();
        this.product = params.product();
        this.duration = params.duration();
        this.startProductionDateTime = params.startProductionDateTime();
        this.endDateTime = params.startProductionDateTime() == null ? null
                : params.startProductionDateTime().plus(params.duration());
        this.emk = params.emk();
        this.placePlan = params.placePlan();
        this.handPackaging = params.handPackaging();
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

    public static Job fromDbJobRow(
            DbJobRow row,
            Product product,
            LocalDateTime startProductionDateTime,
            UnaryOperator<String> nameCleaner) {
        String jobName = row.shortName() != null ? row.shortName().trim() : "";
        if (nameCleaner != null) {
            jobName = nameCleaner.apply(jobName);
        }

        return new Job(new ProductionJobParams(
                String.valueOf(row.snpz()),
                row.lineId(),
                jobName,
                row.snpz(),
                row.np() != null ? row.np() : 0,
                row.quantity() != null ? row.quantity() : 0,
                row.priority() != null ? row.priority() : 0,
                row.mass(),
                product,
                row.duration() != null ? Duration.ofMinutes(row.duration()) : Duration.ZERO,
                startProductionDateTime,
                row.emk() != null ? row.emk() : 0,
                row.placePlan() != null ? row.placePlan() : 0,
                row.isHandPackaging()));
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
        this.priority = priority == 0 ? 1 : priority * 10;
        this.pinned = pinned;
        this.startProductionDateTime = startProductionDateTime;
        this.endDateTime = startProductionDateTime == null ? null : startProductionDateTime.plus(duration);
    }

    public Job(String id, String name, MaintenanceRequest request, Product mProduct) {
        this.id = id;
        this.name = name;
        this.maintenance = true;
        this.product = mProduct;
        this.maintenanceTypeId = request.getMaintenanceTypeId();
        this.maintenanceNote = request.getMaintenanceNote();
        this.duration = Duration.ofMinutes(request.getDurationMinutes());
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
