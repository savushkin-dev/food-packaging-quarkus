package org.acme.foodpackaging.domain;

import java.time.LocalDate;
import java.util.*;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import static org.acme.foodpackaging.service.scheduleoperations.MaintenanceService.createMaintenanceProduct;

@Setter
@Getter
@PlanningSolution
public class PackagingSchedule {

    @ProblemFactProperty
    private WorkCalendar workCalendar;

    @ProblemFactCollectionProperty
    private List<Product> products;

    @PlanningEntityCollectionProperty
    private List<Line> lines;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<Job> jobs;

    private Product maintenanceProduct;
    private Map<Long, Job> allJobsById;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
     private Map<String, ParallelOperation> parallelOperations;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<Job> deletedMaintenance;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<String> overloadedIds;
    private LocalDate dti;
    private String version;

    @PlanningScore
    private HardMediumSoftLongScore score;

    // Ignored by Timefold, used by the UI to display solve or stop solving button
    private SolverStatus solverStatus;

    // No-arg constructor required for Timefold
    public PackagingSchedule() {
        maintenanceProduct = createMaintenanceProduct();
        this.overloadedIds = new HashSet<>();
    }

    public PackagingSchedule(List<Line> lines, LocalDate startDate) {
        maintenanceProduct = createMaintenanceProduct();
        this.overloadedIds = new HashSet<>();

        setWorkCalendar(new WorkCalendar(startDate));
        setLines(lines);
        setDti(startDate);
    }

    public boolean isEmptySolution() {
        return jobs == null || jobs.isEmpty();
    }

    public void setDateForEmptySolution(LocalDate startDate) {
        if (isEmptySolution()) {
            workCalendar.setFromDate(startDate);
            workCalendar.setToDate(startDate.plusDays(1));
            this.dti = startDate;
        }
    }

    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    public Map<String, ParallelOperation> getParallelOperations() {
        return new HashMap<>(parallelOperations);
    }

    public void setParallelOperations(Map<String, ParallelOperation> parallelOperations) {
        this.parallelOperations = parallelOperations == null ? new HashMap<>() : new HashMap<>(parallelOperations);
    }

    public List<Job> getDeletedMaintenance() {
        return new ArrayList<>(deletedMaintenance);
    }

    public void setDeletedMaintenance(List<Job> deletedMaintenance) {
        this.deletedMaintenance = deletedMaintenance == null ? new ArrayList<>() : new ArrayList<>(deletedMaintenance);
    }

    public Set<String> getOverloadedIds() {
        return new HashSet<>(overloadedIds);
    }

    public void setOverloadedIds(Set<String> overloadedIds) {
        this.overloadedIds = overloadedIds == null ? new HashSet<>() : new HashSet<>(overloadedIds);
    }
}