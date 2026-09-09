package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.jobs.MoveJobsRequest;
import org.acme.foodpackaging.dto.request.jobs.PlaceFactRequest;
import org.acme.foodpackaging.dto.request.jobs.SortRangeRequest;
import org.acme.foodpackaging.repository.PackagingScheduleRepository;
import org.acme.foodpackaging.dto.request.jobs.JobSelectionRequest;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.scheduleoperations.MoveJobsService;
import org.acme.foodpackaging.service.scheduleoperations.SortByNpService;
import org.acme.foodpackaging.service.jobs.JobInfoService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;

import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleEditResource {

    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final MoveJobsService moveJobsService;
    private final SortByNpService sortByNpService;
    private final JobRefreshService jobRefreshService;
    private final JobInfoService jobInfoService;
    private final ScheduleSessionService scheduleSessionService;

    @POST
    @Path("sortByNp")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sortByNp(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        sortByNpService.reorderJobsByProductNp(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Sorted successfully").build();
    }

    @POST
    @Path("sortRange")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sortRangeByNp(SortRangeRequest request, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return scheduleSessionService.noScheduleLoadedResponse();
        }

        sortByNpService.sortRangeByNp(schedule, request);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok(Map.of(ApiFields.STATUS, ApiFields.SUCCESS, ApiFields.MESSAGE, "Jobs sorted successfully"))
                .build();
    }

    @POST
    @Path("updateOrderList")
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateOrderList(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return scheduleSessionService.noScheduleLoadedResponse();
        }

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Order list updated for planning").build();
    }

    @POST
    @Path("findCameraFact")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findCameraFact(@HeaderParam("X-Session-Id") String sessionId, PlaceFactRequest placeFactRequest) {
        scheduleSessionService.mutate(sessionId,
                schedule -> jobInfoService.findCameraFact(schedule, placeFactRequest.snpz()));
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "")).build();
    }

    @POST
    @Path("findPlaceFact")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findFactPlace(@HeaderParam("X-Session-Id") String sessionId, PlaceFactRequest placeFactRequest) {
        scheduleSessionService.mutate(sessionId,
                schedule -> jobInfoService.findFactPlace(schedule, placeFactRequest.snpz()));
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "")).build();
    }

    @POST
    @Path("/selection")
    public Response applySelection(@HeaderParam("X-Session-Id") String sessionId, JobSelectionRequest dto) {
        scheduleSessionService.mutateAndResolve(sessionId, schedule -> {
            schedule.getOverloadedIds().clear();
            jobRefreshService.applySelection(dto.selection(), schedule);
        }, solutionManager);
        return Response.ok().build();
    }

    @POST
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
