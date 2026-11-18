package org.acme.foodpackaging.dto.response.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.ParallelOperation;

import java.util.Collection;
import java.util.List;

public record FrontendDataResponse(
        List<Job> jobs,
        List<Line> lines,
        Collection<ParallelOperation> operations,
        HardMediumSoftLongScore score,
        SolverStatus solverStatus
) {}

