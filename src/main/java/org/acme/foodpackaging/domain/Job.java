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

@PlanningEntity
public class Job {

    @PlanningId
    private String id;
    private String name;
    private String np;

    private Product product;
    private int quantity;
    private Duration duration;
    private LocalDateTime minStartTime;
    private LocalDateTime idealEndTime;
    private LocalDateTime maxEndTime;
    private String previousJobId;
    private String nextJobId;

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

    private Map<String, Map<String, Integer>> lineSpeeds;

    /**
     * Start is after cleanup.
     */
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime startCleaningDateTime;
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartCleaningDateTime")
    private LocalDateTime startProductionDateTime;
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

    public Job(String id, String name, String np, Product product, int quantity, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, boolean pinned,
               LocalDateTime startCleaningDateTime, LocalDateTime startProductionDateTime) {
        this.id = id;
        this.name = name;
        this.np = np;
        this.product = product;
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

    public Job(String id, String name, String np, Product product, int quantity, LocalDateTime minStartTime, LocalDateTime idealEndTime, LocalDateTime maxEndTime, int priority, boolean pinned) {
        this(id, name, np, product, quantity, minStartTime, idealEndTime, maxEndTime, priority, pinned, null, null);
    }

    @Override
    public String toString() {
        return id + "(" + product.getName() + ")";
    }

    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    public String getId() {
        return id;
    }

    public String getPreviousJobId() {
        return id;
    }

    public String getNextJobId() {
        return id;
    }

    public Map<String, Map<String, Integer>> getLineSpeeds() {
        return lineSpeeds;
    }

    public int getQuantity() { return quantity; }

    public String getNp() { return np; }

    public String getName() {
        return name;
    }

    public Product getProduct() {
        return product;
    }

    public Duration getDuration() {
        Integer speed = getSpeed();

        if (speed == null || speed <= 0) {
            return Duration.ZERO;
        }
        final int IF_CHANGING_PACKAGING = 4;
        long minutes = (long) Math.ceil(quantity / (double) speed) + IF_CHANGING_PACKAGING;
        return Duration.ofMinutes(minutes);
    }

    public LocalDateTime getMinStartTime() {
        return minStartTime;
    }

    public LocalDateTime getIdealEndTime() {
        return idealEndTime;
    }

    public LocalDateTime getMaxEndTime() {
        return maxEndTime;
    }

    public int getPriority() {
        return priority;
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

    public boolean isPinned() {
        return pinned;
    }

    public Line getLine() { return line; }

    public void setNextJobId(String previousJobId) { this.nextJobId = previousJobId; }

    public void setPreviousJobId(String nextJobId) { this.nextJobId = nextJobId; }

    public void setLine(Line line) {
        this.line = line;
    }

    public Job getPreviousJob() {
        return previousJob;
    }

    public void setPreviousJob(Job previousJob) {
        this.previousJob = previousJob;
    }

    public Job getNextJob() {
        return nextJob;
    }

    public  void setLineSpeeds(Map<String, Map<String, Integer>> lineSpeeds){
        this.lineSpeeds = lineSpeeds;
    }
    public void setNextJob(Job nextJob) {
        this.nextJob = nextJob;
    }

    public LocalDateTime getStartCleaningDateTime() {
        return startCleaningDateTime;
    }

    public void setStartCleaningDateTime(LocalDateTime startCleaningDateTime) {
        this.startCleaningDateTime = startCleaningDateTime;
    }

    public LocalDateTime getStartProductionDateTime() {
        return startProductionDateTime;
    }

    public void setStartProductionDateTime(LocalDateTime startProductionDateTime) {
        this.startProductionDateTime = startProductionDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public void  setProduct(Product product) { this.product = product; }

    // ************************************************************************
    // Complex methods
    // ************************************************************************

    @SuppressWarnings("unused")
    private void updateStartCleaningDateTime() {
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
