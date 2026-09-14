package org.acme.foodpackaging.rest.solution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.dto.request.jobs.PlaceFactRequest;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.jobs.JobInfoService;

import java.util.Map;

@Path("schedule/facts")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class JobFactResource {

    private final JobInfoService jobInfoService;
    private final ScheduleSessionService scheduleSessionService;

    @PUT
    @Path("camera")
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeCameraFact(@HeaderParam("X-Session-Id") String sessionId, PlaceFactRequest placeFactRequest) {
        scheduleSessionService.mutate(sessionId,
                schedule -> jobInfoService.findCameraFact(schedule, placeFactRequest.snpz()));
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "")).build();
    }

    @PUT
    @Path("place")
    @Produces(MediaType.APPLICATION_JSON)
    public Response writePlaceFact(@HeaderParam("X-Session-Id") String sessionId, PlaceFactRequest placeFactRequest) {
        scheduleSessionService.mutate(sessionId,
                schedule -> jobInfoService.findFactPlace(schedule, placeFactRequest.snpz()));
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "")).build();
    }
}
