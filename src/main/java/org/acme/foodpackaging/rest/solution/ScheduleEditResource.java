package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.jobs.MoveJobsRequest;
import org.acme.foodpackaging.dto.request.jobs.JobSelectionRequest;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.scheduleoperations.MoveJobsService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;

import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleEditResource {

    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final MoveJobsService moveJobsService;
    private final JobRefreshService jobRefreshService;
    private final ScheduleSessionService scheduleSessionService;

    @PUT
    @Path("selection")
    public Response applySelection(@HeaderParam("X-Session-Id") String sessionId, JobSelectionRequest dto) {
        scheduleSessionService.mutateAndResolve(sessionId, schedule -> {
            schedule.getOverloadedIds().clear();
            jobRefreshService.applySelection(dto.selection(), schedule);
        }, solutionManager);
        return Response.ok().build();
    }

    @PUT
    @Path("moveJobs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response moveJobs(MoveJobsRequest request, @HeaderParam("X-Session-Id") String sessionId) {
        scheduleSessionService.mutateAndResolve(sessionId,
                schedule -> moveJobsService.moveJobs(schedule, request), solutionManager);
        return Response.ok(Map.of(ApiFields.STATUS, ApiFields.SUCCESS, ApiFields.MESSAGE, "Jobs moved successfully"))
                .build();
    }
}
