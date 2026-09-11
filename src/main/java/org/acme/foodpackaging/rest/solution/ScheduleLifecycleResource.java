package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import ai.timefold.solver.core.api.solver.SolverManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.solution.LoadRequest;
import org.acme.foodpackaging.initializer.ScheduleInitializer;
import org.acme.foodpackaging.repository.PackagingScheduleRepository;
import org.acme.foodpackaging.service.load.LoadDataService;
import org.acme.foodpackaging.service.upload.*;

import org.acme.foodpackaging.service.solution.value.DowntimeDataValue;
import org.acme.foodpackaging.initializer.value.InitDataValue;
import org.acme.foodpackaging.rest.ApiFields;

import java.util.Map;
import static org.acme.foodpackaging.utils.ScheduleUtils.getDowntimeData;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleLifecycleResource {

    private final PackagingScheduleRepository repository;
    private final SolverManager<PackagingSchedule, String> solverManager;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final ScheduleInitializer scheduleInitializer;
    private final LoadDataService loadDataService;
    private final JobSaveService jobSaveService;
    private final UploadDataService uploadDataService;
    private final ScheduleSessionService scheduleSessionService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response init(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        if (!loadDataService.isLoaded()) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }

        InitDataValue data = scheduleInitializer.initSchedule(loadDTO.startDate());
        PackagingSchedule schedule = data.schedule();
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok(data.jobsFromDbRow()).build();
    }

    @POST
    @Path("solve")
    @Produces(MediaType.APPLICATION_JSON)
    public Response solve(@HeaderParam("X-Session-Id") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.SESSION_ID_REQUIRED))
                    .build();
        }

        String problemId = scheduleSessionService.getProblemId(sessionId);

        solverManager.solveBuilder()
                .withProblemId(problemId)
                .withProblemFinder(id -> repository.readForSession(sessionId))
                .withBestSolutionConsumer(schedule -> repository.writeForSession(sessionId, schedule))
                .run();


        return Response.ok(Map.of(
                ApiFields.STATUS, "started",
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Solving started")).build();
    }

    @POST
    @Path("stopSolving")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stopSolving(@HeaderParam("X-Session-Id") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.SESSION_ID_REQUIRED))
                    .build();
        }

        String problemId = scheduleSessionService.getProblemId(sessionId);
        solverManager.terminateEarly(problemId);

        PackagingSchedule finalSchedule = repository.readForSession(sessionId);
        repository.writeForSession(sessionId, finalSchedule);

        DowntimeDataValue response = getDowntimeData(finalSchedule);

        return Response.ok(response).build();
    }

    @POST
    @Path("save")
    public Response save(@HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule bestSolution = scheduleSessionService.requireScheduleForRead(sessionId);

        jobSaveService.saveJobsByType(bestSolution);
        DowntimeDataValue response = getDowntimeData(bestSolution);

        return Response.ok(response).build();
    }

    @POST
    @Path("work")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response work(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);
        if (schedule == null) {
            return scheduleSessionService.noScheduleLoadedResponse();
        }

        uploadDataService.sendToWork(schedule.getJobs());
        return Response.ok(Map.of(ApiFields.MESSAGE, "The task has been sent to work")).build();
    }
}