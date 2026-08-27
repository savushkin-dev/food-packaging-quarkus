package org.acme.foodpackaging.rest.scheduleresource;

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
import org.acme.foodpackaging.dto.DelayNoteRequest;
import org.acme.foodpackaging.dto.MoveJobsRequest;
import org.acme.foodpackaging.dto.PlaceFactRequest;
import org.acme.foodpackaging.dto.SortRangeRequest;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.acme.foodpackaging.record.JobSelection;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.rest.ScheduleSessionService;
import org.acme.foodpackaging.scheduleoperations.MoveJobsService;
import org.acme.foodpackaging.scheduleoperations.SortByNpService;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.jobs.JobInfoService;
import org.acme.foodpackaging.service.jobs.JobNoteService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.jobs.JobService;

import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleEditResource {

    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final JobService jobService;
    private final JobNoteService jobNoteService;
    private final MoveJobsService moveJobsService;
    private final SortByNpService sortByNpService;
    private final JobRefreshService jobRefreshService;
    private final JobInfoService jobInfoService;
    private final AlignSolutionService alignSolutionService;
    private final ScheduleSessionService scheduleSessionService;

    @POST
    @Path("delayNote")
    public Response delayNote(@HeaderParam("X-Session-Id") String sessionId, DelayNoteRequest request) {
        scheduleSessionService.mutate(sessionId, schedule -> jobNoteService.writeDelayNote(request, schedule));
        return Response.ok("Note is written").build();
    }

    @POST
    @Path("cleaningDelay")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cleaningDelayNote(@HeaderParam("X-Session-Id") String sessionId, DelayNoteRequest request) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        jobNoteService.writeCleaningDelayNote(request, schedule);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Note is written").build();
    }

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
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
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
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
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
                schedule -> jobInfoService.findCameraFact(schedule, placeFactRequest.getSnpz()));
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "")).build();
    }

    @POST
    @Path("findPlaceFact")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findFactPlace(@HeaderParam("X-Session-Id") String sessionId, PlaceFactRequest placeFactRequest) {
        scheduleSessionService.mutate(sessionId,
                schedule -> jobInfoService.findFactPlace(schedule, placeFactRequest.getSnpz()));
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "")).build();
    }

    @POST
    @Path("/selection")
    public Response applySelection(@HeaderParam("X-Session-Id") String sessionId, JobSelection dto) {
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

    @POST
    @Path("alignPlan")
    @Produces(MediaType.APPLICATION_JSON)
    public Response alignPlan(@HeaderParam("X-Session-Id") String sessionId) {
        scheduleSessionService.mutateAndResolve(sessionId, alignSolutionService::alignFromScratch, solutionManager);
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, ApiFields.REFRESH_OK)).build();
    }

    @POST
    @Path("resetAlign")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetAlign(@HeaderParam("X-Session-Id") String sessionId) {
        scheduleSessionService.mutateAndResolve(sessionId, alignSolutionService::reset, solutionManager);
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, ApiFields.REFRESH_OK)).build();
    }
}
