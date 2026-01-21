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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;

@Getter
@Setter
@NoArgsConstructor
@PlanningEntity
public class Job {

    @PlanningId
    private String id;
    private Long fId;
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
    private boolean maintenance;
    private Integer maintenanceTypeId;

    private LocalDateTime startProductionDateTimeFact;
    private LocalDateTime minStartTime;
    private LocalDateTime idealEndTime;
    private LocalDateTime maxEndTime;

    /**
     * Higher priority is a higher number.
     */
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

    // ************************************************************************
    // Parameter objects for simplified constructors
    // ************************************************************************

    /**
     * Parameters for creating a regular production job.
     */
    public record ProductionJobParams(
            String id,
            String lineId,
            Long snpz,
            int np,
            String name,
            Product product,
            double mass,
            int quantity,
            Duration duration,
            int priority,
            LocalDateTime startProductionDateTime,
            Integer maintenanceTypeId,
            String maintenanceNote
    ) {}

    /**
     * Parameters for creating a maintenance job with time constraints.
     */
    public record MaintenanceJobTimeParams(
            String id,
            String name,
            Product product,
            Duration duration,
            LocalDateTime minStartTime,
            LocalDateTime idealEndTime,
            LocalDateTime maxEndTime,
            int priority,
            boolean pinned,
            LocalDateTime startCleaningDateTime,
            LocalDateTime startProductionDateTime
    ) {}

    // ************************************************************************
    // Simplified constructors using parameter objects
    // ************************************************************************

    /**
     * Constructor for regular production jobs.
     * Package-private - use factory methods for public API.
     */
    Job(ProductionJobParams params) {
        this.id = params.id();
        this.lineId = params.lineId();
        this.snpz = params.snpz();
        this.np = params.np();
        this.name = params.name();
        this.maintenanceNote = params.maintenanceNote();
        this.product = params.product();
        this.mass = params.mass();
        this.quantity = params.quantity();
        this.duration = params.duration();
        this.maintenanceTypeId = params.maintenanceTypeId();
        this.priority = params.priority() == 0 ? 1 : params.priority() * 10;
        this.startProductionDateTime = params.startProductionDateTime();
        this.endDateTime = params.startProductionDateTime() == null ? null 
                : params.startProductionDateTime().plus(params.duration());
    }

    /**
     * Constructor for maintenance jobs with time constraints.
     * Package-private - use factory methods for public API.
     */
    Job(MaintenanceJobTimeParams params) {
        this.id = params.id();
        this.name = params.name();
        this.product = params.product();
        this.duration = params.duration();
        this.minStartTime = params.minStartTime();
        this.idealEndTime = params.idealEndTime();
        this.maxEndTime = params.maxEndTime();
        this.priority = params.priority() == 0 ? 1 : params.priority() * 10;
        this.pinned = params.pinned();
        this.startCleaningDateTime = params.startCleaningDateTime();
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
    public static Job fromDbJobRow(org.acme.foodpackaging.record.DbJobRow row, Product product, LocalDateTime startProductionDateTime, java.util.function.Function<String, String> nameCleaner) {
        String jobName = row.shortName() != null ? row.shortName().trim() : "";
        if (nameCleaner != null) {
            jobName = nameCleaner.apply(jobName);
        }
        
        return new Job(new ProductionJobParams(
                String.valueOf(row.snpz()),
                row.lineId(),
                row.snpz(),
                row.np() != null ? row.np() : 0,
                jobName,
                product,
                row.mass(),
                row.quantity() != null ? row.quantity() : 0,
                row.duration() != null ? Duration.ofMinutes(row.duration()) : Duration.ZERO,
                row.priority() != null ? row.priority() : 0,
                startProductionDateTime,
                null,
                null
        ));
    }

    /**
     * Creates a maintenance job from a database row.
     * 
     * @param row The maintenance database row
     * @param maintenanceTypeName The name of the maintenance type
     * @param maintenanceProduct The maintenance product
     * @param startProductionDateTime The start production date/time (can be null)
     * @return A new maintenance Job instance
     */
    public static Job fromDbMaintenanceRow(org.acme.foodpackaging.dto.DbMaintenanceRow row, String maintenanceTypeName, Product maintenanceProduct, LocalDateTime startProductionDateTime) {
        Job job = new Job(new ProductionJobParams(
                String.valueOf(row.getFId()),
                row.getLineId(),
                row.getSnpz(),
                -1,
                maintenanceTypeName,
                maintenanceProduct,
                -1.0,
                -1,
                row.getDuration() != null ? Duration.ofMinutes(row.getDuration()) : Duration.ZERO,
                0,
                startProductionDateTime,
                row.getMaintenanceTypeId(),
                row.getMaintenanceNote()
        ));
        job.setFId(row.getFId());
        job.setMaintenance(true);
        return job;
    }

    /**
     * Creates a maintenance job from a maintenance request.
     * 
     * @param id The unique job ID
     * @param lineId The line ID
     * @param maintenanceTypeId The maintenance type ID
     * @param maintenanceTypeName The name of the maintenance type
     * @param maintenanceNote Optional maintenance note
     * @param maintenanceProduct The maintenance product
     * @param durationMinutes The duration in minutes
     * @return A new maintenance Job instance
     */
    public static Job createMaintenanceJob(String id, String lineId, Integer maintenanceTypeId, String maintenanceTypeName, String maintenanceNote, Product maintenanceProduct, int durationMinutes) {
        Job job = new Job(new ProductionJobParams(
                id,
                lineId,
                0L,
                -1,
                maintenanceTypeName,
                maintenanceProduct,
                -1.0,
                -1,
                Duration.ofMinutes(durationMinutes),
                0,
                null,
                maintenanceTypeId,
                maintenanceNote
        ));
        job.setMaintenance(true);
        return job;
    }

    /**
     * Creates a job with time constraints (used for maintenance jobs with scheduling constraints).
     * Public for backward compatibility with tests - prefer other factory methods for production code.
     * 
     * @deprecated Use factory methods like {@link #createMaintenanceJob(String, String, Integer, String, String, Product, int)} instead.
     * This method is kept for test compatibility.
     */
    @Deprecated
    @SuppressWarnings("java:S107")
    public Job(String id, String name, Product product, Duration duration, 
               LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime,
               int priority, boolean pinned, LocalDateTime startCleaningDateTime, 
               LocalDateTime startProductionDateTime) {
        this.id = id;
        this.name = name;
        this.product = product;
        this.duration = duration;
        this.minStartTime = minStartTime;
        this.idealEndTime = idealEndTime;
        this.maxEndTime = maxEndTime;
        this.priority = priority == 0 ? 1 : priority * 10;
        this.pinned = pinned;
        this.startCleaningDateTime = startCleaningDateTime;
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
        if(isMaintenance()) return duration;

        Integer speed = getSpeed();

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
        LocalDateTime startCleaning;
        LocalDateTime startProduction;
        if (previous == null) {
            startCleaning = line.getStartDateTime();
            startProduction = line.getStartDateTime();
        } else {
            startCleaning = previous.getEndDateTime();
            if (startCleaning != null && getProduct() != null && previous.getProduct() != null) {
                try {
                    Duration cleanupDuration = getProduct().getCleanupDuration(previous.getProduct());
                    startProduction = startCleaning.plus(cleanupDuration);
                } catch (IllegalArgumentException | NullPointerException e) {
                    // If cleanup duration is missing or cleaningDurations map is null, using zero duration as fallback
                    // This can happen if cleaning durations were not properly initialized
                    // For maintenance jobs, cleanup duration should be zero anyway
                    startProduction = startCleaning;
                }
            } else {
                startProduction = startCleaning;
            }
        }
        setStartCleaningDateTime(startCleaning);
        setStartProductionDateTime(startProduction);
        var endTime = startProduction == null ? null : startProduction.plus(getDuration());
        setEndDateTime(endTime);
    }
}
