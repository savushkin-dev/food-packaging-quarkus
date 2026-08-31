package org.acme.foodpackaging.rest.scheduleresource;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class MaintenanceResource {

    private final MaintenanceJob maintenanceJob;
    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;

    /**
     * Суточная мойка линий
     */
    @POST
    @Path("dailyCleaning")
    @Produces(MediaType.TEXT_PLAIN)
    public Response dailyCleaning(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }
        maintenanceJob.addDailyFullCleaning(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Cleanings added successfully").build();
    }

    /**
     * Операции для сервисной работы на линии
     */
    @POST
    @Path("maintenance")
    public Response addMaintenance(MaintenanceRequest request,
                                   @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);
        PackagingSchedule updated;
        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }
        if (request.isUpdateLineMode()) {
            if (request.getMaintenanceTypeId() != null) {
                updated = maintenanceJob.updateMaintenanceType(schedule, request);
            } else {
                updated = maintenanceJob.updateDuration(schedule, request);
            }
        } else if (request.isRemoveLineMode()) {
            updated = maintenanceJob.removeMaintenanceJob(schedule, request);
        } else {
            updated = maintenanceJob.addMaintenanceJob(schedule, request);
        }
        solutionManager.update(updated, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, updated);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Maintenance job added",
                ApiFields.LINE_ID, request.getLineId())).build();
    }
}
