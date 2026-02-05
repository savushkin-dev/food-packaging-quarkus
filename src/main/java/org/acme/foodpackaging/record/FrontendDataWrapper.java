package org.acme.foodpackaging.record;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;

import java.util.List;

public record FrontendDataWrapper(
        List<Job> jobs,
        List<Line> lines,
        HardMediumSoftLongScore score,
        SolverStatus solverStatus
) {}

