package org.acme.foodpackaging.rest.solution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.dto.response.solution.DowntimeDataResponse;
import org.acme.foodpackaging.service.load.DowntimeDataService;

import java.time.Duration;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class DowntimeResource {

    private final DowntimeDataService downtimeDataService;

    @GET
    @Path("downtimePeriods/{idBatch}")
    @Produces(MediaType.APPLICATION_JSON)
    public DowntimeDataResponse downtimePeriods(@PathParam("idBatch") String idBatch,
                                                @QueryParam("duration") Integer duration) {
        if (idBatch == null || idBatch.isBlank()) {
            throw new WebApplicationException("Batch id is required", Response.Status.BAD_REQUEST);
        }
        String trimmed = idBatch.trim();
        if (duration == null) {
            return downtimeDataService.build(trimmed);
        }
        if (duration < 0) {
            throw new WebApplicationException("Query parameter 'duration' must be >= 0", Response.Status.BAD_REQUEST);
        }
        return downtimeDataService.build(trimmed, Duration.ofMinutes(duration.longValue()));
    }
}
