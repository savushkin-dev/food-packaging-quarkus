package org.acme.foodpackaging.rest;

import ai.timefold.solver.core.api.solver.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.bootstrap.LoadData;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.dto.MoveJobsRequestDTO;
import org.acme.foodpackaging.dto.PinRequestDTO;
import org.acme.foodpackaging.dto.*;
import org.acme.foodpackaging.persistence.*;
import org.acme.foodpackaging.scheduleOperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleOperations.MoveJobsService;
import org.acme.foodpackaging.scheduleOperations.PinService;
import org.acme.foodpackaging.scheduleOperations.SortByNpService;
import org.acme.foodpackaging.service.load.ScheduleBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import static io.smallrye.config._private.ConfigLogging.log;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.*;
import static org.acme.foodpackaging.sql.SqlQueries.DELETE_SOLUTION_JSON;

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
    LoadData loadData;
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

    @ConfigProperty(name = "dbLabeling.url")
    String dbLabelingUrl;
    @ConfigProperty(name = "db.url")
    String dbUrl;

    public PackagingScheduleResource(){
        loadData = new LoadData();
    }

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
        if (loadData == null) {
            throw new WebApplicationException("No data loaded", Response.Status.NOT_FOUND);
        }
        return loadData.getLinesIdWithNamesMap();
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

            loadData.sendToWork(schedule.getJobs());
            return Response.ok(Map.of("message", "The task has been sent to work")).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Failed send task to work: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("load")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response load(LoadDTO loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        LocalDate startDate = loadDTO.getStartDate();

        try {
            if (!loadDTO.getFindSolvedInDb()) {
                PackagingSchedule createdSchedule = buildNewSchedule(loadDTO);
                repository.writeForSession(sessionId, createdSchedule);
                return Response.ok(Map.of(
                        "message", "New schedule generated (forced) for date: " + startDate
                )).build();
            }

            PackagingSchedule schedule = tryImportScheduleFromDb(startDate);
            if (schedule != null && isScheduleCompatible(schedule, loadDTO)) {

                solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_SHADOW_VARIABLES_ONLY);
                repository.writeForSession(sessionId, schedule);
                return Response.ok(Map.of(
                        "message", "Saved schedule imported for date: " + startDate
                )).build();
            }

            PackagingSchedule newSchedule = buildNewSchedule(loadDTO);
            repository.writeForSession(sessionId, newSchedule);
            return Response.ok(Map.of(
                    "message", "No saved schedule found — new data generated for date: " + startDate
            )).build();

        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid date format. Please use YYYY-MM-DD"))
                    .build();

        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Failed to load schedule: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("loadpday")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loadpday(LoadDTO loadDTO) {

        LocalDate startDate = loadDTO.getStartDate();
        LocalDate endDate = loadDTO.getEndDate();

        try {
            Map<String, Map<String, Object>> res = loadData.loadPDay(startDate, endDate);
            return Response.ok(res).build();

        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid date format. Please use YYYY-MM-DD"))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Failed to load production order: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("updatepday")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatepday(Map<String, LocalDate> mapsnpz) {
        try {
            loadData.updatePDay(mapsnpz);

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Jobs DTF updates successfully"
            )).build();

        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid date format. Please use YYYY-MM-DD"))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Failed to update jobs: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("lineStart")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineStartTime(@HeaderParam("X-Session-Id") String sessionId, TimeUpdateDTO request) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }
        Line line = findLineById(schedule, request.getLineId());

        setLineStartDateTime(line, request.getStartLineDateTime());
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok(Map.of(
                "status", "success",
                "sessionId", sessionId,
                "message", "Line start time updated"
        )).build();
    }

    @POST
    @Path("planEndTime")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePlanningEndTime(@HeaderParam("X-Session-Id") String sessionId, TimeUpdateDTO request) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }

        schedule.getWorkCalendar().setMaxEndDateTime(request.getMaxEndDateTime());
        fixEndDateTime(schedule.getJobs(), request.getMaxEndDateTime());
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok(Map.of(
                "status", "success",
                "sessionId", sessionId,
                "message", "MaxEndDateTime updated"
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

        loadData.refreshJobsNextDay(schedule);
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
     * Сохраняет план в бд в формате json строки
     */
    @POST
    @Path("saveToDb")
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveToDb(@HeaderParam("X-Session-Id") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Session ID is required"))
                    .build();
        }

        JsonExporter jsonExporter = new JsonExporter(dbUrl);
        try {
            //Извлекаем план пользователя (черновик пользователя) по sessionId для того чтобы сохранить общий план
            PackagingSchedule bestSolution = repository.readForSession(sessionId);
            if (bestSolution == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No solution found for session: " + sessionId))
                        .build();
            }

            jsonExporter.export(bestSolution);
            return Response.ok(Map.of("message", "Saved to DB successfully")).build();
        } catch (Exception e) {
            return Response.serverError().entity("Save error: " + e.getMessage()).build();
        }
    }
    /**
     * Удаляет план из бд
     */
    @POST
    @Path("removeSolution")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSolutionByDate(@HeaderParam("X-Session-Id") String sessionId) {

        //Получаем план для текущей сессии чтобы из него выявить дату удаляемого плана (но можно просто передавать дату в запросе как параметр как вариант)
        PackagingSchedule currentSchedule = repository.readForSession(sessionId);
        if (currentSchedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded for session"))
                    .build();
        }

        String date = currentSchedule.getWorkCalendar().getFromDate().toString();

        if (date.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("status", "error", "message", "Date field not set on server"))
                    .build();
        }
        LocalDate removeDate = LocalDate.parse(date);
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(DELETE_SOLUTION_JSON)) {
            stmt.setDate(1, java.sql.Date.valueOf(removeDate));
            int updatedRows = stmt.executeUpdate();

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Solution removed for date: " + date + " (rows affected: " + updatedRows + ")"
            )).build();

        } catch (SQLException e) {
            return Response.serverError()
                    .entity(Map.of("status", "error", "message", "SQL error: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(@HeaderParam("X-Session-Id") String sessionId) {
        try {
            PackagingSchedule schedule = repository.readForSession(sessionId);

            if (schedule == null) {
                return Response.status(Response.Status.NO_CONTENT)
                        .entity(Map.of("status", "error", "message", "No schedule available to export."))
                        .build();
            }
            System.out.println(sessionId);
            PlanFactAnalysis factAnalysis = new PlanFactAnalysis(
                    schedule.getWorkCalendar().getFromDate().toString()
            );
            factAnalysis.excelWrite(schedule.getJobs());
            File planFactFile = factAnalysis.getExportFile();

            if (planFactFile != null && planFactFile.exists()) {
                byte[] fileContent = Files.readAllBytes(planFactFile.toPath());
                return Response.ok(fileContent)
                        .header("Content-Disposition", "attachment; filename=\"" + planFactFile.getName() + "\"")
                        .build();
            }

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Export completed successfully. Excel file saved in resources."
            )).build();

        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("status", "error", "message", "Export error: " + e.getMessage()))
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

    private PackagingSchedule tryImportScheduleFromDb(LocalDate startDate) {
        try {
            JsonImporter importer = new JsonImporter(dbUrl, startDate);
            return importer.importFromDb();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private PackagingSchedule buildNewSchedule(LoadDTO loadDTO) {
        return scheduleBuilder.buildSchedule(
                loadDTO,
                loadDTO.toLineStartDateTimeMap()
        );
    }

    public File exportTimeCompare(String date, PackagingSchedule solution) {
        ExcelExporter exporter = new ExcelExporter(dbLabelingUrl, date, solution.getJobs());
        return exporter.getExportedFile();
    }
}