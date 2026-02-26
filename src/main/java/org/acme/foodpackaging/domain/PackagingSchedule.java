package org.acme.foodpackaging.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.Getter;
import lombok.Setter;

import static org.acme.foodpackaging.scheduleoperations.MaintenanceJob.createMaintenanceProduct;

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
    private List<Job> deletedMaintenance;

    @PlanningScore
    private HardMediumSoftLongScore score;

    // Ignored by Timefold, used by the UI to display solve or stop solving button
    private SolverStatus solverStatus;

    // No-arg constructor required for Timefold
    public PackagingSchedule() {
        maintenanceProduct = createMaintenanceProduct();
        this.deletedMaintenance = new ArrayList<>();
    }

    public boolean isEmptySolution() {
        return jobs == null || jobs.isEmpty();
    }

    public void setDateForEmptySolution(LocalDate startDate) {
        if (isEmptySolution()) {
            workCalendar.setFromDate(startDate);
            workCalendar.setToDate(startDate.plusDays(1));
        }
    }
    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    @Override
    public String toString() {
        return "PackagingSchedule{" +
                "workCalendar=" + workCalendar +
                ", products=" + products +
                ", lines=" + lines +
                ", jobs=" + jobs +
                ", score=" + score +
                ", solverStatus=" + solverStatus +
                '}';

    }
}