package org.acme.foodpackaging.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningPinToIndex;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@PlanningEntity
public class Line {

    @PlanningId
    private String id;
    private String name;
    private String operator;

    private LocalDateTime startDateTime;
    private LocalDateTime maxEndTime;

    @JsonIgnore
    @PlanningListVariable
    private List<Job> jobs;

    private int firstUnpinnedIndex;

    // No-arg constructor required for Timefold
    public Line() {
    }

    public Line(String id, String name, String operator, LocalDateTime startDateTime) {
        this.id = id;
        this.name = name;
        this.operator = operator;
        this.startDateTime = startDateTime;
        jobs = new ArrayList<>();
    }

    public Line(String id, String name) {
        this.id = id;
        this.name = name;
        jobs = new ArrayList<>();
    }

    @Override
    public String toString() {
        return name;
    }

    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    @PlanningPinToIndex
    public int getFirstUnpinnedIndex() { return firstUnpinnedIndex; }

}
