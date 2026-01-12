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

    private Long snpz;
    private int np;
    private int quantity;

    private double mass;

    private Product product;
    private Duration duration;
    private boolean maintenance;

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

    // Constructor for common construction pattern (15 parameters)
    public Job(String id, String lineId, Long snpz, int np, String name, Product product, double mass, int quantity, Duration duration, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, LocalDateTime startCleaningDateTime, LocalDateTime startProductionDateTime) {
        this.id = id;
        this.lineId = lineId;
        this.snpz = snpz;
        this.np = np;
        this.name = name;
        this.product = product;
        this.mass = mass;
        this.quantity = quantity;
        this.duration = duration;
        this.minStartTime = minStartTime;
        this.idealEndTime = idealEndTime;
        this.maxEndTime = maxEndTime;
        this.priority = priority == 0 ? 1 : priority * 10;
        this.startCleaningDateTime = startCleaningDateTime;
        this.startProductionDateTime = startProductionDateTime;
        this.endDateTime = startProductionDateTime == null ? null : startProductionDateTime.plus(duration);
    }

    // Constructor for maintenance job construction (11 parameters)
    public Job(String id, String name, Product product, Duration duration, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, boolean pinned, LocalDateTime startCleaningDateTime, LocalDateTime startProductionDateTime) {
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
            startProduction = startCleaning == null ? null : startCleaning.plus(getProduct().getCleanupDuration(previous.getProduct()));
        }
        setStartCleaningDateTime(startCleaning);
        setStartProductionDateTime(startProduction);
        var endTime = startProduction == null ? null : startProduction.plus(getDuration());
        setEndDateTime(endTime);
    }
}
