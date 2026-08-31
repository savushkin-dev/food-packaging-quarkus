package org.acme.foodpackaging.rest.scheduleresource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.dto.DowntimePeriodsResponse;
import org.acme.foodpackaging.dto.LoadRequest;
import org.acme.foodpackaging.persistence.load.DowntimePeriodsService;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.repository.solution.PlrPlanRepository;
import org.acme.foodpackaging.rest.ApiFields;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ReferenceDataResource {

    private final PlrPlanRepository plrPlanRepository;
    private final DowntimePeriodsService downtimePeriodsService;
    private final LoadDataService loadDataService;

    @GET
    @Path("lines")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getLines() {
        if (!loadDataService.isLoaded()) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }
        return loadDataService.getLines();
    }

    @GET
    @Path("serviceTypes")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<Integer, String> getMaintenanceTypes() {
        if (!loadDataService.isLoaded()) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }
        return loadDataService.getMaintenanceTypes();
    }

    @POST
    @Path("refreshData")
    @Produces(MediaType.APPLICATION_JSON)
    public Response refreshData() {
        loadDataService.refresh();
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Data refreshed successfully from database")).build();
    }

    @POST
    @Path("versionsByDate")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getPlanVersions(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {
        return plrPlanRepository.findDistinctVersionsByDate(loadDTO.getStartDate().atStartOfDay().toLocalDate());
    }

    @GET
    @Path("downtimePeriods/{idBatch}")
    @Produces(MediaType.APPLICATION_JSON)
    public DowntimePeriodsResponse downtimePeriods(@PathParam("idBatch") String idBatch,
                                                   @QueryParam("duration") Integer duration) {
        if (idBatch == null || idBatch.isBlank()) {
            throw new WebApplicationException("Batch id is required", Response.Status.BAD_REQUEST);
        }
        String trimmed = idBatch.trim();
        if (duration == null) {
            return downtimePeriodsService.build(trimmed);
        }
        if (duration < 0) {
            throw new WebApplicationException("Query parameter 'duration' must be >= 0", Response.Status.BAD_REQUEST);
        }
        return downtimePeriodsService.build(trimmed, Duration.ofMinutes(duration.longValue()));
    }
}

