package org.acme.foodpackaging.rest.scheduleresource;

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
import org.acme.foodpackaging.record.FrontendDataWrapper;
import org.acme.foodpackaging.record.LineProductionDto;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.rest.ScheduleSessionService;
import org.acme.foodpackaging.service.lines.LineService;

import java.time.LocalDate;
import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleReadResource {

    private final SolverManager<PackagingSchedule, String> solverManager;
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
    public FrontendDataWrapper getFrontendData(@HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = scheduleSessionService.requireScheduleForRead(sessionId);
        return new FrontendDataWrapper(
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
            @QueryParam("selectedDate") String selectedDate,
            @QueryParam("shiftNumber") Integer shiftNumber) {

        if (selectedDate == null || selectedDate.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, "selectedDate is required"))
                    .build();
        }

        PackagingSchedule solution = scheduleSessionService.requireSchedule(sessionId);

        Map<String, LineProductionDto> productions = lineService.calculateLineProductions(
                solution.getLines(),
                LocalDate.parse(selectedDate),
                shiftNumber);

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
