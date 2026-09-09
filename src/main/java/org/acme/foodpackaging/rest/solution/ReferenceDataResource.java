package org.acme.foodpackaging.rest.solution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.service.load.LoadDataService;
import org.acme.foodpackaging.rest.ApiFields;

import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ReferenceDataResource {

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

}
