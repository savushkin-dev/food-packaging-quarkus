package org.acme.foodpackaging.rest.scheduleresource;

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
import org.acme.foodpackaging.dto.LoadRequest;
import org.acme.foodpackaging.initializer.ScheduleInitializer;
import org.acme.foodpackaging.initializer.ScheduleVersionInitializer;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.persistence.upload.JobSaveService;
import org.acme.foodpackaging.persistence.upload.SolutionVersionExportService;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.DowntimeData;
import org.acme.foodpackaging.record.InitData;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.rest.ScheduleSessionService;
import org.acme.foodpackaging.service.builder.ScheduleBuilder;
import org.acme.foodpackaging.service.builder.ScheduleBuilderByVersion;

import java.util.Map;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.getDowntimeData;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleLifecycleResource {

    private final PackagingScheduleRepository repository;
    private final SolverManager<PackagingSchedule, String> solverManager;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final ScheduleInitializer scheduleInitializer;
    private final ScheduleVersionInitializer scheduleVersionInitializer;
    private final LoadDataService loadDataService;
    private final JobSaveService jobSaveService;
    private final SolutionVersionExportService exportService;
    private final UploadDataService uploadDataService;
    private final ScheduleSessionService scheduleSessionService;

    @POST
    @Path("init")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response init(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        if (!loadDataService.isLoaded()) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }

        InitData data = scheduleInitializer.initSchedule(loadDTO.getStartDate());
        PackagingSchedule schedule = data.schedule();
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok(data.jobsFromDbRow()).build();
    }

    @POST
    @Path("initVersion")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response initVersion(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        if (!loadDataService.isLoaded()) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }

        PackagingSchedule solution = scheduleVersionInitializer.initSchedule(loadDTO.getStartDate(), loadDTO.getVersion());
        solution.setVersion(loadDTO.getVersion());
        solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Solution version imported from json")).build();
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

        String problemId = getProblemId(sessionId);

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

        String problemId = getProblemId(sessionId);
        solverManager.terminateEarly(problemId);

        PackagingSchedule finalSchedule = repository.readForSession(sessionId);
        repository.writeForSession(sessionId, finalSchedule);

        DowntimeData response = getDowntimeData(finalSchedule);

        return Response.ok(response).build();
    }

    private String getProblemId(String sessionId) {
        return sessionId != null ? sessionId : "default";
    }

    @POST
    @Path("save")
    public Response save(@HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule bestSolution = scheduleSessionService.requireScheduleForRead(sessionId);

        jobSaveService.saveJobsByType(bestSolution);
        DowntimeData response = getDowntimeData(bestSolution); // TODO: уточнить источник метода

        return Response.ok(response).build();
    }

    /**
     * Сохраняет план в json определенной версии
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces(MediaType.APPLICATION_JSON)
    @Path("saveVersion")
    public Response saveVersion(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule bestSolution = scheduleSessionService.requireScheduleForRead(sessionId);

        if (bestSolution.getVersion() == null && loadDTO.getVersion() == null) {
            bestSolution.setVersion("V1");
        } else {
            bestSolution.setVersion(loadDTO.getVersion());
        }
        exportService.export(bestSolution, bestSolution.getVersion());
        return Response.ok(Map.of(ApiFields.MESSAGE, "Saved to PlrPLan successfully")).build();
    }

    @POST
    @Path("work")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response work(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);
        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        uploadDataService.sendToWork(schedule.getJobs());
        return Response.ok(Map.of(ApiFields.MESSAGE, "The task has been sent to work")).build();
    }
}

