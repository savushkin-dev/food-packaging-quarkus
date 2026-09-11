package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.align.AlignSolutionService;

import java.util.Map;

@Path("schedule/alignment")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class AlignmentResource {

    private final AlignSolutionService alignSolutionService;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final ScheduleSessionService scheduleSessionService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response align(@HeaderParam("X-Session-Id") String sessionId) {
        scheduleSessionService.mutateAndResolve(sessionId, alignSolutionService::alignFromScratch, solutionManager);
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, ApiFields.REFRESH_OK)).build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetAlign(@HeaderParam("X-Session-Id") String sessionId) {
        scheduleSessionService.mutateAndResolve(sessionId, alignSolutionService::reset, solutionManager);
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, ApiFields.REFRESH_OK)).build();
    }
}
