package org.acme.foodpackaging.rest.scheduleresource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.ParallelOperation;
import org.acme.foodpackaging.dto.request.paralleloperations.*;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.scheduleoperations.ParallelOperationService;

import java.util.Collection;
import java.util.Map;

@Path("schedule/parallel-operations")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ParallelOperationResource {

    private final ParallelOperationService parallelOperationService;
    private final PackagingScheduleRepository repository;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getParallelOperations(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        Collection<ParallelOperation> operations = schedule.getParallelOperations().values();

        return Response.ok(operations).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addParallelOperation(AddParallelOperationRequest request,
                                         @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        PackagingSchedule updated = parallelOperationService.add(schedule, request);
        repository.writeForSession(sessionId, updated);

        return Response.status(Response.Status.CREATED).entity(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Parallel operation added")).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateParallelOperation(UpdateParallelOperationRequest request,
                                            @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        PackagingSchedule updated = parallelOperationService.update(schedule, request);
        repository.writeForSession(sessionId, updated);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Parallel operation updated")).build();
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeParallelOperation(@PathParam("id") String id,
                                            @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        PackagingSchedule updated = parallelOperationService.remove(schedule, id);
        repository.writeForSession(sessionId, updated);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Parallel operation removed")).build();
    }

    private PackagingSchedule requireSchedule(String sessionId) {
        return repository.readForSession(sessionId);
    }

    private Response noScheduleLoaded() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                .build();
    }
}