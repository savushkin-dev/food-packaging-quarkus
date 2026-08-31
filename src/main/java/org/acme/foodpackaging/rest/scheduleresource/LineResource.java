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
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.PinRequest;
import org.acme.foodpackaging.dto.TimeUpdate;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.scheduleoperations.PinService;
import org.acme.foodpackaging.service.lines.LineService;

import java.util.Map;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class LineResource {

    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final LineService lineService;
    private final PinService pinService;

    @POST
    @Path("lineStart")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineStartTime(@HeaderParam("X-Session-Id") String sessionId, TimeUpdate request) {

        PackagingSchedule solution = repository.readForSession(sessionId);

        if (solution == null || request.getStartLineDateTime() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }
        Line line = findLineById(solution, request.getLineId());
        if (!line.getJobs().isEmpty()) {
            setLineStartDateTime(line, request.getStartLineDateTime());

            solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
            lineService.setMaxEndDateTimeByLastJob(solution);
            repository.writeForSession(sessionId, solution);
            return Response.ok(Map.of(
                    ApiFields.STATUS, ApiFields.SUCCESS,
                    ApiFields.SESSION_ID, sessionId,
                    ApiFields.MESSAGE, "Line start time updated")).build();
        }
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Line has jobs. Start time is not updated")).build();
    }
    @POST
    @Path("lineMaxEnd")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineMaxEndTime(@HeaderParam("X-Session-Id") String sessionId, TimeUpdate request) {

        PackagingSchedule solution = repository.readForSession(sessionId);

        if (solution == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        Line line = findLineById(solution, request.getLineId());

        setLineMaxEndDateTime(line, request.getLineMaxEndDateTime());
        solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Line end time updated")).build();
    }

    /**
     * Закрепеляет/открепляет задачи на линииях
     */
    @POST
    @Path("pin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response pin(PinRequest pinRequest, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule solution = repository.readForSession(sessionId);
        if (solution == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        Line line = findLineById(solution, pinRequest.getLineId());

        pinService.pinLine(line, pinRequest);

        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Line " + line.getId() + " updated successfully.")).build();
    }
}
