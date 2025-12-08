package org.acme.foodpackaging.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.NextElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@PlanningEntity
public class Job {

    @Getter
    @PlanningId
    private String id;
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private String previousJobId;
    @Setter
    @Getter
    private String nextJobId;
    @Setter
    @Getter
    private int snpz;
    @Getter
    private int np;
    @Getter
    private int quantity;
    @Getter
    @Setter
    private double mass;

    @Getter
    @Setter
    private Product product;
    @Setter
    private Duration duration;
    @Getter
    @Setter
    private boolean maintenance;
    @Getter
    @Setter
    private LocalDateTime minStartTime;
    @Getter
    @Setter
    private LocalDateTime idealEndTime;
    @Getter
    @Setter
    private LocalDateTime maxEndTime;

    /**
     * Higher priority is a higher number.
     */
    @Getter
    private int priority;
    @Setter
    @Getter
    @PlanningPin
    private boolean pinned;

    @Setter
    @Getter
    @InverseRelationShadowVariable(sourceVariableName = "jobs")
    private Line line;
    @Setter
    @Getter
    @JsonIgnore
    @PreviousElementShadowVariable(sourceVariableName = "jobs")
    private Job previousJob;
    @Setter
    @Getter
    @JsonIgnore
    @NextElementShadowVariable(sourceVariableName = "jobs")
    private Job nextJob;

    @Setter
    @Getter
    private Map<String, Map<String, Integer>> lineSpeeds;
    /**
     * Start is after cleanup.
     */
    @Setter
    @Getter
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime startCleaningDateTime;
    @Setter
    @Getter
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime startProductionDateTime;
    @Setter
    @Getter
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime endDateTime;

    // No-arg constructor required for Timefold
    public Job() {
    }

    public Job(String id, String name, Product product, Duration duration, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, boolean pinned) {
        this(id, name, product, duration, minStartTime, idealEndTime, maxEndTime, priority, pinned, null, null);
    }

    public Job(String id, String name, Product product, Duration duration, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, boolean pinned,
               LocalDateTime startCleaningDateTime, LocalDateTime startProductionDateTime) {
        this.id = id;
        this.name = name;
        this.product = product;
        this.duration = duration;
        this.minStartTime = minStartTime;
        this.idealEndTime = idealEndTime;
        this.maxEndTime = maxEndTime;
        this.priority = priority == 0 ? 1 : priority*10;
        this.startCleaningDateTime = startCleaningDateTime;
        this.startProductionDateTime = startProductionDateTime;
        this.endDateTime = startProductionDateTime == null ? null : startProductionDateTime.plus(duration);
        this.pinned = pinned;
    }

    public Job(String id, String name, int snpz, int np, Product product, double mass, int quantity, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, boolean pinned,
               LocalDateTime startCleaningDateTime, LocalDateTime startProductionDateTime) {
        this.id = id;
        this.name = name;
        this.snpz = snpz;
        this.np = np;
        this.product = product;
        this.mass = mass;
        this.quantity = quantity;
        this.minStartTime = minStartTime;
        this.idealEndTime = idealEndTime;
        this.maxEndTime = maxEndTime;
        this.startCleaningDateTime = startCleaningDateTime;
        this.startProductionDateTime = startProductionDateTime;
        this.endDateTime = startProductionDateTime == null ? null : startProductionDateTime.plus(getDuration());
        this.pinned = pinned;
        this.priority = priority == 0 ? 1 : priority*10;
    }

    public Job(String id, String name, int snpz, int np, Product product, double mass, int quantity, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, boolean pinned) {
        this(id, name, snpz, np, product, mass, quantity, minStartTime, idealEndTime, maxEndTime, priority, pinned, null, null);
    }

    @Override
    public String toString() {
        return id + "(" + product.getName() + ")";
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
        if (line == null || product == null || product.getType() == null) {
            return null;
        }
        Map<String, Integer> productSpeeds = lineSpeeds.get(line.getId());
        if (productSpeeds == null) {
            return null;
        }
        return productSpeeds.get(product.getType());
    }

    public LocalDateTime getMaxEndDateTime() {
        return maxEndTime;
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
