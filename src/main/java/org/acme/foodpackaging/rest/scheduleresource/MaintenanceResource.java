package org.acme.foodpackaging.rest.scheduleresource;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.maintenance.AddMaintenanceRequest;
import org.acme.foodpackaging.dto.request.maintenance.UpdateMaintenanceRequest;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.scheduleoperations.MaintenanceService;

import java.util.Map;

@Path("schedule/maintenance")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class MaintenanceResource {

    private final MaintenanceService maintenanceService;
    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;

    /**
     * Суточная мойка линий
     */

    @POST
    @Path("dailyCleaning")
    @Produces(MediaType.TEXT_PLAIN)
    public Response dailyCleaning(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        maintenanceService.addDailyFullCleaning(schedule);
        persist(sessionId, schedule);

        return Response.ok("Cleanings added successfully").build();
    }

    /**
     * Операции для сервисной работы на линии
     */

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addMaintenance(AddMaintenanceRequest request,
            @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        PackagingSchedule updated = maintenanceService.addMaintenanceJob(schedule, request);
        persist(sessionId, updated);

        return Response.status(Response.Status.CREATED).entity(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Maintenance job added",
                ApiFields.LINE_ID, request.lineId())).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateMaintenance(UpdateMaintenanceRequest request,
            @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        PackagingSchedule updated = maintenanceService.updateMaintenanceJob(schedule, request);
        persist(sessionId, updated);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Maintenance job updated",
                ApiFields.LINE_ID, request.lineId())).build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeMaintenance(@QueryParam("lineId") String lineId,
            @QueryParam("removeIndex") int removeIndex,
            @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = requireSchedule(sessionId);
        if (schedule == null) {
            return noScheduleLoaded();
        }

        PackagingSchedule updated = maintenanceService.removeMaintenanceJob(schedule, lineId, removeIndex);
        persist(sessionId, updated);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Maintenance job removed",
                ApiFields.LINE_ID, lineId)).build();
    }

    private PackagingSchedule requireSchedule(String sessionId) {
        return repository.readForSession(sessionId);
    }

    private Response noScheduleLoaded() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                .build();
    }

    private void persist(String sessionId, PackagingSchedule updated) {
        solutionManager.update(updated, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, updated);
    }
}