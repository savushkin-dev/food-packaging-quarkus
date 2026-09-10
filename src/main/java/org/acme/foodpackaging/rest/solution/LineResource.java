package org.acme.foodpackaging.rest.solution;

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
import org.acme.foodpackaging.dto.request.lines.PinRequest;
import org.acme.foodpackaging.dto.request.lines.LineTimeUpdateRequest;
import org.acme.foodpackaging.repository.PackagingScheduleRepository;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.scheduleoperations.PinService;
import org.acme.foodpackaging.service.lines.LineService;

import java.util.Map;

import static org.acme.foodpackaging.utils.ScheduleUtils.*;
import static org.acme.foodpackaging.utils.ScheduleUtils.findLineById;
import static org.acme.foodpackaging.utils.ScheduleUtils.setLineStartDateTime;

@Path("schedule/line")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class LineResource {

    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final LineService lineService;
    private final PinService pinService;

    @PUT
    @Path("start")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineStartTime(@HeaderParam("X-Session-Id") String sessionId, LineTimeUpdateRequest request) {

        PackagingSchedule solution = requireSchedule(sessionId);

        if (solution == null || request.startLineDateTime() == null) {
            return noScheduleLoaded();
        }
        Line line = findLineById(solution, request.lineId());
        if (!line.getJobs().isEmpty()) {
            setLineStartDateTime(line, request.startLineDateTime());

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

    @PUT
    @Path("maxEnd")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineMaxEndTime(@HeaderParam("X-Session-Id") String sessionId, LineTimeUpdateRequest request) {

        PackagingSchedule solution = requireSchedule(sessionId);

        if (solution == null) {
            return noScheduleLoaded();
        }

        Line line = findLineById(solution, request.lineId());

        setLineMaxEndDateTime(line, request.lineMaxEndDateTime());
        persist(sessionId, solution);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Line end time updated")).build();
    }

    /**
     * Закрепляет/открепляет задачи на линиях
     */
    @PUT
    @Path("pin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response pin(PinRequest pinRequest, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule solution = requireSchedule(sessionId);
        if (solution == null) {
            return noScheduleLoaded();
        }

        Line line = findLineById(solution, pinRequest.lineId());

        if (line == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.LINE_NOT_FOUND))
                    .build();
        }

        pinService.pinLine(line, pinRequest);

        // solutionManager.update() здесь не вызывается —
        // только запись в репозиторий.
        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Line " + line.getId() + " updated successfully.")).build();
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
