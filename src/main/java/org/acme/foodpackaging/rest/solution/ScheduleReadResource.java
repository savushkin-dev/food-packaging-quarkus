package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.response.solution.FrontendDataResponse;
import org.acme.foodpackaging.repository.PackagingScheduleRepository;

import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.lines.LineService;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleReadResource {

    private final SolverManager<PackagingSchedule, String> solverManager;
    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final LineService lineService;
    private final ScheduleSessionService scheduleSessionService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PackagingSchedule get(@HeaderParam("X-Session-Id") String sessionId) {
        SolverStatus solverStatus = solverManager.getSolverStatus(scheduleSessionService.getProblemId(sessionId));
        PackagingSchedule schedule = scheduleSessionService.requireScheduleForRead(sessionId);
        schedule.setSolverStatus(solverStatus);
        return schedule;
    }

    @GET
    @Path("frontData")
    @Produces(MediaType.APPLICATION_JSON)
    public FrontendDataResponse getFrontendData(@HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = scheduleSessionService.requireScheduleForRead(sessionId);
        return new FrontendDataResponse(
                schedule.getJobs(),
                schedule.getLines(),
                schedule.getScore(),
                schedule.getSolverStatus());
    }

    @GET
    @Path("dailyProductions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dailyProductions(
            @HeaderParam("X-Session-Id") String sessionId,
            @QueryParam("shiftStart") String shiftStartParam) {

        if (shiftStartParam == null || shiftStartParam.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, "shiftStart query parameter is required"))
                    .build();
        }

        LocalDateTime shiftStart;
        try {
            shiftStart = LocalDateTime.parse(shiftStartParam);
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, "shiftStart must be in ISO format (yyyy-MM-ddTHH:mm:ss)"))
                    .build();
        }

        PackagingSchedule solution = repository.readForSession(sessionId);

        if (solution == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        Map<String, Object> productions = lineService.calculateLineProductions(solution.getLines(), shiftStart);

        return Response.ok(productions).build();
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces(MediaType.APPLICATION_JSON)
    @Path("analyze")
    public ScoreAnalysis<HardMediumSoftLongScore> analyze(
            @QueryParam("fetchPolicy") ScoreAnalysisFetchPolicy fetchPolicy,
            @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule problem = scheduleSessionService.requireScheduleForRead(sessionId);
        return fetchPolicy == null ? solutionManager.analyze(problem) : solutionManager.analyze(problem, fetchPolicy);
    }
}