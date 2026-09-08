package org.acme.foodpackaging.dto.response.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;

import java.util.List;

public record FrontendDataResponse(
        List<Job> jobs,
        List<Line> lines,
        HardMediumSoftLongScore score,
        SolverStatus solverStatus
) {}

