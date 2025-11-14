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
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.dto.MoveJobsRequestDTO;
import org.acme.foodpackaging.dto.PinRequestDTO;
import org.acme.foodpackaging.persistence.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

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

    @ConfigProperty(name = "dbLabeling.url")
    String dbLabelingUrl;

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
        if (loadData == null) {
            throw new WebApplicationException("No data loaded", Response.Status.NOT_FOUND);
        }
        return loadData.getLinesIdWithNamesMap();
    }

    @POST
    @Path("load")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response load(LoadDTO loadDTO, @HeaderParam("X-Session-Id") String sessionId) {
        LocalDate startDate = loadDTO.getStartDate();

        try {
            PackagingSchedule schedule = tryImportScheduleFromDb(startDate);

            if ((schedule != null) && isScheduleCompatible(schedule, loadDTO)) {
                solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_SHADOW_VARIABLES_ONLY);
                repository.writeForSession(sessionId, schedule);
                return Response.ok(Map.of(
                        "message", "Saved schedule imported for date: " + startDate
                )).build();
            }

            PackagingSchedule newSchedule = loadData.loadDataByDate(
                    loadDTO.getStartDate(),
                    loadDTO.getEndDate(),
                    loadDTO.getIdealEndDateTime(),
                    loadDTO.getMaxEndDateTime(),
                    loadDTO.toLineStartDateTimeMap()
            );

            repository.writeForSession(sessionId, newSchedule);

            return Response.ok(Map.of(
                    "message", "New data generated successfully for date: " + loadDTO.getStartDate()
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
    public Response loadpday(LoadDTO loadDTO, @HeaderParam("X-Session-Id") String sessionId) {
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
    public Response updatepday(Map<String, LocalDate> mapsnpz, @HeaderParam("X-Session-Id") String sessionId) {
        try {

            PackagingSchedule currentSchedule = repository.readForSession(sessionId);
            if (currentSchedule == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "No schedule loaded for session"))
                        .build();
            }

            LocalDate startDate = currentSchedule.getWorkCalendar().getFromDate();
            LocalDate endDate = currentSchedule.getWorkCalendar().getToDate();

            loadData.updatePDay(startDate, endDate, mapsnpz);

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
                        SolutionPostProcessor.sortJobsByNp(schedule);
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

        return Response.ok(Map.of(
                "status", "stopped",
                "sessionId", sessionId,
                "message", "Solving stopped"
        )).build();
    }

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

        Line fromLine = schedule.getLines().stream()
                .filter(l -> l.getId().equals(request.getFromLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("fromLineId not found"));
        Line toLine = schedule.getLines().stream()
                .filter(l -> l.getId().equals(request.getToLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("toLineId not found"));

        List<Job> jobsToMove = fromLine.getJobs().subList(
                request.getFromIndex(),
                Math.min(request.getFromIndex() + request.getCount(), fromLine.getJobs().size())
        );

        for (Job job : jobsToMove) {
            String productType = job.getProduct().getType();
            String toLineId = toLine.getId();

            Integer duration = job.getLineSpeeds()
                    .getOrDefault(toLineId, Map.of())
                    .get(productType);

            if (duration == null || duration == 0) {
                String message = String.format(
                        "Cannot move job \"%s\" to line \"%s\": This type of product is not produced on this line.",
                        job.getName(),
                        toLine.getName()
                );

                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", message))
                        .build();
            }
        }

        List<Job> movedJobs = moveSubList(fromLine, request.getFromIndex(), request.getCount(),
                toLine, request.getInsertIndex());

        fixLineJobs(fromLine);
        fixLineJobs(toLine);
        fixPinIndexes(schedule);
        SolutionPostProcessor.sortJobsByNp(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Jobs moved and sorted successfully"
        )).build();
    }

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

        Line pinnedLine = solution.getLines().stream()
                .filter(l -> l.getId().equals(pinRequest.getLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + pinRequest.getLineId()));

        if (Boolean.TRUE.equals(pinRequest.getPinAll())) {
            pinnedLine.setFirstUnpinnedIndex(pinnedLine.getJobs().size());
            repository.writeForSession(sessionId, solution);
            return Response.ok(Map.of(
                    "status", "success",
                    "message", "All jobs on line " + pinnedLine.getId() + " were pinned successfully."
            )).build();
        }

        if (pinRequest.getPinCount() != null) {
            int count = pinRequest.getPinCount();

            if (count <= 0) {
                pinnedLine.setFirstUnpinnedIndex(0);
                repository.writeForSession(sessionId, solution);
                return Response.ok(Map.of(
                        "status", "success",
                        "message", "All jobs were unpinned (pinCount = 0)."
                )).build();
            }

            int safeCount = Math.min(count, pinnedLine.getJobs().size());
            pinnedLine.setFirstUnpinnedIndex(safeCount);
            repository.writeForSession(sessionId, solution);

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "First " + safeCount + " jobs were pinned successfully."
            )).build();
        }

        pinnedLine.setFirstUnpinnedIndex(0);
        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Line " + pinnedLine.getId() + " was fully unpinned."
        )).build();
    }

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

        if (date == null || date.isBlank()) {
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

    private List<Job> moveSubList(Line fromLine, int fromIndex, int count, Line toLine, int insertIndex) {
        List<Job> fromJobs = new ArrayList<>(fromLine.getJobs());
        List<Job> toJobs = new ArrayList<>(toLine.getJobs());

        List<Job> subList = new ArrayList<>(fromJobs.subList(fromIndex, fromIndex + count));
        fromJobs.subList(fromIndex, fromIndex + count).clear();
        toJobs.addAll(insertIndex, subList);

        fromLine.setJobs(fromJobs);
        toLine.setJobs(toJobs);

        return subList;
    }

    private void fixLineJobs(Line line) {
        List<Job> jobs = line.getJobs();
        for (int i = 0; i < jobs.size(); i++) {
            Job current = jobs.get(i);
            current.setLine(line);
            current.setPreviousJob(i > 0 ? jobs.get(i - 1) : null);
            current.setNextJob(i < jobs.size() - 1 ? jobs.get(i + 1) : null);
            current.updateStartCleaningDateTime();
        }
    }

    private void fixPinIndexes(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            int jobCount = line.getJobs().size();
            int firstUnpinned = line.getFirstUnpinnedIndex();
            if (firstUnpinned > jobCount) {
                line.setFirstUnpinnedIndex(jobCount);
            }
        }
    }

    private PackagingSchedule tryImportScheduleFromDb(LocalDate startDate) {
        try {
            JsonImporter importer = new JsonImporter(dbUrl, startDate);
            return importer.importFromDb();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isScheduleCompatible(PackagingSchedule schedule, LoadDTO loadDTO) {
        if (schedule.getLines().size() != loadDTO.getLineStartTimes().size()) {
            return false;
        }

        if (!Objects.equals((schedule.getJobs().get(0).getMaxEndTime()), loadDTO.getMaxEndDateTime())) return false;
        if (!Objects.equals((schedule.getJobs().get(0).getIdealEndTime()), loadDTO.getIdealEndDateTime())) return false;

        Map<String, LocalDateTime> startTimesFromJson = loadDTO.toLineStartDateTimeMap();

        for (Line line : schedule.getLines()) {
            LocalTime lineStartTime = line.getStartDateTime().toLocalTime();
            LocalTime expectedStart = startTimesFromJson.get(line.getId()).toLocalTime();

            if (!lineStartTime.equals(expectedStart)) {
                return false;
            }
        }
        return true;
    }

    public File exportTimeCompare(String date, PackagingSchedule solution) {
        ExcelExporter exporter = new ExcelExporter(dbLabelingUrl, date, solution.getJobs());
        return exporter.getExportedFile();
    }
}