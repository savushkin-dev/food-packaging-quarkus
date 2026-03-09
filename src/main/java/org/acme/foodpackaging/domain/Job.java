package org.acme.foodpackaging.domain;

import java.time.Duration;
import java.time.LocalDateTime;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.NextElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import org.acme.foodpackaging.record.CleaningResult;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.MaintenanceJobParams;
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
    private Long fId;
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
    private Duration delayDuration;
    private boolean maintenance;
    private boolean handPackaging;
    private boolean finalDuration;
    private Integer maintenanceTypeId;

    private LocalDateTime cameraStart;
    private LocalDateTime cameraEnd;
    private LocalDateTime dtv;
    private LocalDateTime dti;
    private LocalDateTime startProductionDateTimeFact;
    private LocalDateTime minStartTime;
    private LocalDateTime idealEndTime;
    private LocalDateTime maxEndTime;
    private LocalDateTime planEndDateTime;
    private Integer emk;
    private String placeFactInfo;
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
    }

    /**
     * Constructor for maintenance jobs with time constraints.
     * Package-private - use factory methods for public API.
     */
   private Job(MaintenanceJobParams params) {
        this.id = params.id();
        this.lineId = params.lineId();
        this.name = params.name();
        this.maintenanceNote = params.note();
        this.maintenanceTypeId = params.typeId();
        this.product = params.product();
        this.duration = params.duration();
        this.priority = params.priority() == 0 ? 1 : params.priority() * 10;
        this.pinned = params.pinned();
        this.startProductionDateTime = params.startProductionDateTime();
        this.endDateTime = params.startProductionDateTime() == null ? null 
                : params.startProductionDateTime().plus(params.duration());

    }

    /**
     * Creates a regular production job from a database row.
     * 
     * @param row The database row containing job data
     * @param product The product for this job
     * @param startProductionDateTime The start production date/time (can be null)
     * @param nameCleaner Optional function to clean the job name (can be null to use raw name)
     * @return A new Job instance
     */
    public static Job fromDbJobRow(
        DbJobRow row,
        Product product,
        LocalDateTime startProductionDateTime,
        java.util.function.UnaryOperator<String> nameCleaner
) {
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
                row.placePlan()
        ));
    }

    /**
     * Creates a maintenance job from a database row.
     * 
     * @param row The maintenance database row
     * @param maintenanceName The name of the maintenance type
     * @param maintenanceProduct The maintenance product
     * @param startProductionDateTime The start production date/time (can be null)
     * @return A new maintenance Job instance
     */
    public static Job fromDbMaintenanceRow(org.acme.foodpackaging.dto.DbMaintenanceRow row, String maintenanceName, Product maintenanceProduct, LocalDateTime startProductionDateTime) {
        Job job = new Job(new MaintenanceJobParams(
                String.valueOf(row.getFId()),
                row.getLineId(),
                maintenanceName,
                row.getMaintenanceNote(),
                row.getMaintenanceTypeId(),
                maintenanceProduct,
                row.getDuration() != null ? Duration.ofMinutes(row.getDuration()) : Duration.ZERO,
                0, true, startProductionDateTime

        ));
        job.setFId(row.getFId());
        job.setMaintenance(true);
        return job;
    }

    /**
     * Creates a maintenance job from a maintenance request.
     * 
     * @param id The unique job ID
     * @param maintenanceTypeId The maintenance type ID
     * @param maintenanceName The name of the maintenance type
     * @param maintenanceNote Optional maintenance note
     * @param maintenanceProduct The maintenance product
     * @param durationMinutes The duration in minutes
     * @return A new maintenance Job instance
     */
    public static Job createMaintenanceJob(String id, String lineId, Integer maintenanceTypeId, String maintenanceName, String maintenanceNote, Product maintenanceProduct, int durationMinutes) {
        Job job = new Job(new MaintenanceJobParams(
                id, lineId,
                maintenanceName,
                maintenanceNote,
                maintenanceTypeId,
                maintenanceProduct,
                Duration.ofMinutes(durationMinutes),
                0, true, null
        ));
        job.setMaintenance(true);
        return job;
    }

    /**
     * Creates a job with time constraints (used for maintenance jobs with scheduling constraints).
     * Public for backward compatibility with tests - prefer other factory methods for production code.
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

    @Override
    public String toString() {
        return id + "(" + (product != null ? product.getName() : "null") + ")";
    }

    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    public Duration getDuration() {
        if(isMaintenance() || isFinalDuration()) return duration;

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
        return Duration.ofMinutes(minutes);
    }

    @JsonIgnore
    public Integer getSpeed() {
        if (line == null || product == null || product.getType() == null) return null;
        return SpeedCacheUtils.getSpeed(line.getId(), product.getType());
    }

    @JsonIgnore
    public Integer getHandPackagingSpeed() {
        if (line == null || product == null || product.getType() == null) return null;
        return SpeedCacheUtils.getHandPackagingSpeed(line.getId(), product.getType());
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
        setPlanEndDateTime(delayDuration == null || endDateTime == null ? null : endDateTime.minus(delayDuration));
    }

    private LocalDateTime computeStartProduction(Job previous, LocalDateTime startCleaning) {
        if (previous == null || startCleaning == null || getProduct() == null || previous.getProduct() == null) {
            return startCleaning;
        }
        try {
            CleaningResult meta = product.getCleaningResults().get(previous.getProduct());
            Duration cleanupDuration = meta.isPLRLC()
                    ? Duration.ofMinutes(CleaningDurationUtils.getLinesCleaning().get(line.getId()))
                    : product.getCleaningDurations().get(previous.getProduct());
            return startCleaning.plus(cleanupDuration);
        } catch (IllegalArgumentException | NullPointerException e) {
            return startCleaning;
        }
    }   
}
