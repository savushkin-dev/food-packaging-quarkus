package org.acme.foodpackaging.rest.solution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.dto.request.jobs.DelayNoteRequest;
import org.acme.foodpackaging.service.jobs.JobNoteService;

@Path("schedule/notes")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class NoteResource {

    private final JobNoteService jobNoteService;
    private final ScheduleSessionService scheduleSessionService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response writeDelayNote(@HeaderParam("X-Session-Id") String sessionId, DelayNoteRequest request) {
        scheduleSessionService.mutate(sessionId, schedule -> jobNoteService.writeDelayNote(request, schedule));
        return Response.ok("Note is written").build();
    }

    @PUT
    @Path("cleaning")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeCleaningDelayNote(@HeaderParam("X-Session-Id") String sessionId, DelayNoteRequest request) {
        scheduleSessionService.mutate(sessionId, schedule -> jobNoteService.writeCleaningDelayNote(request, schedule));
        return Response.ok("Note is written").build();
    }
}
