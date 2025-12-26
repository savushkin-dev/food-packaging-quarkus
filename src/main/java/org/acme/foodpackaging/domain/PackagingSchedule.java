package org.acme.foodpackaging.domain;

import java.time.LocalDate;
import java.util.HashMap;
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
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.DbMaintenanceRow;

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

    private Map<Integer, DbJobRow> dbJobRowMap;

    private Map<Integer, DbMaintenanceRow> dbMaintenanceRowMap;

    private  Map<Integer, Job> jobIdMap;

    @PlanningScore
    private HardMediumSoftLongScore score;

    // Ignored by Timefold, used by the UI to display solve or stop solving button
    private SolverStatus solverStatus;

    // No-arg constructor required for Timefold
    public PackagingSchedule() {
        jobIdMap = new HashMap<>();
    }

    public boolean isEmptySolution() {
        return jobs == null || jobs.isEmpty();
    }

    public void setDateForEmptySolution(LocalDate startDate ) {
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
