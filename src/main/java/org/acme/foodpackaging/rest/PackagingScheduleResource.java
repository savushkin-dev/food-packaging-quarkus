package org.acme.foodpackaging.rest;

import ai.timefold.solver.core.api.solver.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.dto.MoveJobsRequestDTO;
import org.acme.foodpackaging.dto.PinRequestDTO;
import org.acme.foodpackaging.dto.*;
import org.acme.foodpackaging.persistence.*;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.scheduleOperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleOperations.MoveJobsService;
import org.acme.foodpackaging.scheduleOperations.PinService;
import org.acme.foodpackaging.scheduleOperations.SortByNpService;
import org.acme.foodpackaging.service.builder.ScheduleBuilder;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.jobs.JobSaveService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.util.*;

import static io.smallrye.config._private.ConfigLogging.log;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.*;

@Path("schedule")
@ApplicationScoped
public class PackagingScheduleResource {

    @Inject
    PackagingScheduleRepository repository;
    @Inject
    SolverManager<PackagingSchedule, String> solverManager;
    @Inject
    SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    @Inject
    MaintenanceJob maintenanceJob;
    @Inject
    MoveJobsService moveJobsService;
    @Inject
    SortByNpService sortByNpService;
    @Inject
    PinService pinService;
    @Inject
    ScheduleBuilder scheduleBuilder;
    @Inject
    LoadDataService loadDataService;
    @Inject
    UploadDataService uploadDataService;
    @Inject
    JobRepository jobRepository;
    @Inject
    JobRefreshService jobRefreshService;
    @Inject
    JobSaveService jobSaveService;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PackagingSchedule get(@HeaderParam("X-Session-Id") String sessionId) {

        SolverStatus solverStatus = solverManager.getSolverStatus(getProblemId(sessionId));
        PackagingSchedule schedule = repository.readForSession(sessionId);
        if (schedule == null) {
            throw new WebApplicationException("No schedule loaded", Response.Status.NOT_FOUND);
        }
        schedule.setSolverStatus(solverStatus);
        return schedule;
    }

    @GET
    @Path("lines")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getLines() {
        if (loadDataService == null) {
            throw new WebApplicationException("No data loaded", Response.Status.NOT_FOUND);
        }
        return loadDataService.getLines();
    }

    @POST
    @Path("work")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response work(@HeaderParam("X-Session-Id") String sessionId) {

        try {
            PackagingSchedule schedule = repository.readForSession(sessionId);
            if (schedule == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "No schedule loaded"))
                        .build();
            }
            uploadDataService.sendToWork(schedule.getJobs());
            return Response.ok(Map.of("message", "The task has been sent to work")).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Failed send task to work: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("init")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<DbJobRow> init(LoadDTO loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        LocalDate startDate = loadDTO.getStartDate();

            PackagingSchedule schedule = scheduleBuilder.buildSchedule(loadDTO.getStartDate());
            solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
            repository.writeForSession(sessionId, schedule);

            if (loadDataService == null) {
                throw new WebApplicationException("No data loaded", Response.Status.NOT_FOUND);
            }

            return jobRepository.getDbJobRowList(schedule.getDbJobRowMap());
    }

    @POST
    @Path("/selection")
    public Response applySelection(@HeaderParam("X-Session-Id") String sessionId, JobSelectionDTO dto) {

        PackagingSchedule updatedSchedule = jobRefreshService.applySelection(dto.selection(),
                repository.readForSession(sessionId));

        solutionManager.update(updatedSchedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId,updatedSchedule);

        return Response.ok().build();
    }

    @POST
    @Path("lineStart")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineStartTime(@HeaderParam("X-Session-Id") String sessionId, TimeUpdateDTO request) {

        PackagingSchedule solution = repository.readForSession(sessionId);

        if (solution == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }
        Line line = findLineById(solution, request.getLineId());
        if(line.getJobs().isEmpty()) {
            setLineStartDateTime(line, request.getStartLineDateTime());

            solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
            repository.writeForSession(sessionId, solution);
            return Response.ok(Map.of(
                    "status", "success",
                    "sessionId", sessionId,
                    "message", "Line start time updated"
            )).build();
        }
        return Response.ok(Map.of(
                "status", "success",
                "sessionId", sessionId,
                "message", "Line has jobs. Start time is not updated"
        )).build();
    }

    @POST
    @Path("lineMaxEnd")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineMaxEndTime(@HeaderParam("X-Session-Id") String sessionId, TimeUpdateDTO request) {

        PackagingSchedule solution = repository.readForSession(sessionId);

        if (solution == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }

        Line line = findLineById(solution, request.getLineId());

        setLineMaxEndDateTime(line, request.getLineMaxEndDateTime());
        solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                "status", "success",
                "sessionId", sessionId,
                "message", "Line end time updated"
        )).build();
    }

    @POST
    @Path("updateOrderList")
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateOrderList(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Order list updated for planning").build();
    }

    @POST
    @Path("sortByNp")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sortByNp(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        sortByNpService.reorderJobsByProductNp(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Sorted successfully").build();
    }

    @POST
    @Path("solve")
    @Produces(MediaType.APPLICATION_JSON)
    public Response solve(@HeaderParam("X-Session-Id") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Session ID is required"))
                    .build();
        }

        try {
            String problemId = getProblemId(sessionId);

            solverManager.solveBuilder()
                    .withProblemId(problemId)
                    .withProblemFinder(id -> repository.readForSession(sessionId))
                    .withBestSolutionConsumer(schedule -> {
                        repository.writeForSession(sessionId, schedule);
                    })
                    .run();

            return Response.ok(Map.of(
                    "status", "started",
                    "sessionId", sessionId,
                    "message", "Solving started"
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to start solving: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("stopSolving")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stopSolving(@HeaderParam("X-Session-Id") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Session ID is required"))
                    .build();
        }

        String problemId = getProblemId(sessionId);
        solverManager.terminateEarly(problemId);

        PackagingSchedule finalSchedule = repository.readForSession(sessionId);
        repository.writeForSession(sessionId, finalSchedule);

        return Response.ok(Map.of(
                "status", "stopped",
                "sessionId", sessionId,
                "message", "Solving stopped"
        )).build();
    }
    /**
     * Перемещение задач на линиях
     */
    @POST
    @Path("moveJobs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response moveJobs(MoveJobsRequestDTO request, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }

        try {
            PackagingSchedule result = moveJobsService.moveJobs(schedule, request);

            solutionManager.update(result, SolutionUpdatePolicy.UPDATE_ALL);
            repository.writeForSession(sessionId, result);

            return Response.ok(Map.of("status", "success", "message", "Jobs moved successfully")).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to move jobs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Internal error"))
                    .build();
        }
    }
    /**
     * Операции для сервисной работы на линии
     */
    @POST
    @Path("maintenance")
    public Response addMaintenance(MaintenanceRequestDTO request,
                                   @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);
        PackagingSchedule updated;
        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }
            if(request.isUpdateLineMode()){
                updated = maintenanceJob.updateDuration(schedule, request);
            }
            else if(request.isRemoveLineMode()){
                updated = maintenanceJob.removeMaintenanceJob(schedule, request);
            }
            else{
                updated = maintenanceJob.addMaintenanceJob(schedule, request);
            }
        solutionManager.update(updated, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, updated);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Maintenance job added",
                "lineId", request.getLineId()
        )).build();
    }
    /**
     * Закрепеляет/открепляет задачи на линииях
     */
    @POST
    @Path("pin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response pin(PinRequestDTO pinRequest, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule solution = repository.readForSession(sessionId);
        if (solution == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }

        Line line = findLineById(solution, pinRequest.getLineId());

        pinService.pinLine(line, pinRequest);

        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Line " + line.getId() + " updated successfully."
        )).build();
    }

    /**
     * Сохраняет план
     */
    @POST
    @Path("save")
    public Response save(@HeaderParam("X-Session-Id") String sessionId) {

        try {
            PackagingSchedule bestSolution = repository.readForSession(sessionId);
            if (bestSolution == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No solution for session"))
                        .build();
            }
            jobSaveService.saveJobsByType(bestSolution);
            return Response.ok(Map.of("message", "Saved successfully")).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Saving error: " + e.getMessage()))
                    .build();
        }

    }


    @PUT
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("analyze")
    public ScoreAnalysis<HardMediumSoftLongScore> analyze(@QueryParam("fetchPolicy") ScoreAnalysisFetchPolicy fetchPolicy,
                                                          @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule problem = repository.readForSession(sessionId);
        return fetchPolicy == null ? solutionManager.analyze(problem) : solutionManager.analyze(problem, fetchPolicy);
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private String getProblemId(String sessionId) {
        return sessionId != null ? sessionId : "default";
    }

}