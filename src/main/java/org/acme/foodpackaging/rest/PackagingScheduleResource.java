package org.acme.foodpackaging.rest;

import ai.timefold.solver.core.api.solver.*;
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
public class PackagingScheduleResource {

    public static final String SINGLETON_SOLUTION_ID = "1";

    private PackagingScheduleRepository repository;

    private SolverManager<PackagingSchedule, String> solverManager;

    private SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;

    private SolverJob<PackagingSchedule, String> currentSolverSolution;

    @Inject
    public PackagingScheduleResource(PackagingScheduleRepository repository,
            SolverManager<PackagingSchedule, String> solverManager,
            SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager) {
        this.repository = repository;
        this.solverManager = solverManager;
        this.solutionManager = solutionManager;
    }

    @Inject
    LoadData loadData;

    PinRequestDTO pinRequest;

    MoveJobsRequestDTO moveJobsRequest;

    @ConfigProperty(name = "dbLabeling.url")
    String dbLabelingUrl;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    String date;

    @POST
    @Path("load")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response load(LoadDTO loadDTO) {
        LocalDate startDate = loadDTO.getStartDate();
        this.date = startDate.toString();

        try {
            PackagingSchedule schedule = tryImportScheduleFromDb(startDate);

            if (schedule != null && isScheduleCompatible(schedule, loadDTO)) {
                solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_SHADOW_VARIABLES_ONLY);
                repository.write(schedule);
                return Response.ok(Map.of(
                        "message", "Saved schedule imported for date: " + startDate
                )).build();
            }

            loadData.loadDataByDate(
                    loadDTO.getStartDate(),
                    loadDTO.getEndDate(),
                    loadDTO.getIdealEndDateTime(),
                    loadDTO.getMaxEndDateTime(),
                    loadDTO.toLineStartDateTimeMap()
            );

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
    @Path("moveJobs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response moveJobs(MoveJobsRequestDTO request) {
        PackagingSchedule schedule = repository.read();
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
        repository.write(schedule);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Jobs moved and sorted successfully"
        )).build();
    }
    /**
     * Перемещает подсписок из одного списка в другой и возвращает перемещённые задачи
     */
    private List<Job> moveSubList(Line fromLine, int fromIndex, int count,
                                  Line toLine, int insertIndex) {
        List<Job> fromJobs = new ArrayList<>(fromLine.getJobs());
        List<Job> toJobs = new ArrayList<>(toLine.getJobs());

        List<Job> subList = new ArrayList<>(fromJobs.subList(fromIndex, fromIndex + count));
        fromJobs.subList(fromIndex, fromIndex + count).clear();
        toJobs.addAll(insertIndex, subList);

        // Назначаем новые списки обратно в линии
        fromLine.setJobs(fromJobs);
        toLine.setJobs(toJobs);

        return subList;
    }
    /**
     * Восстанавливает previous/next и пересчитывает shadow variables в линии
     */
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
    /**
     * Корректирует FirstUnpinnedIndex
     */
    private void fixPinIndexes(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            int jobCount = line.getJobs().size();
            int firstUnpinned = line.getFirstUnpinnedIndex();
            if (firstUnpinned > jobCount) {
                line.setFirstUnpinnedIndex(jobCount);
            }
        }
    }


    @POST
    @Path("pin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response pin(PinRequestDTO pinRequest) {
        PackagingSchedule solution = repository.read();

        Line pinnedLine = solution.getLines().stream()
                .filter(l -> l.getId().equals(pinRequest.getLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + pinRequest.getLineId()));

        if (Boolean.TRUE.equals(pinRequest.getPinAll())) {
            pinnedLine.setFirstUnpinnedIndex(pinnedLine.getJobs().size());
            repository.write(solution);
            return Response.ok(Map.of(
                    "status", "success",
                    "message", "All jobs on line " + pinnedLine.getId() + " were pinned successfully."
            )).build();
        }

        if (pinRequest.getPinCount() != null) {
            int count = pinRequest.getPinCount();

            if (count <= 0) {
                pinnedLine.setFirstUnpinnedIndex(0);
                repository.write(solution);
                return Response.ok(Map.of(
                        "status", "success",
                        "message", "All jobs were unpinned (pinCount = 0)."
                )).build();
            }

            int safeCount = Math.min(count, pinnedLine.getJobs().size());
            pinnedLine.setFirstUnpinnedIndex(safeCount);
            repository.write(solution);

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "First " + safeCount + " jobs were pinned successfully."
            )).build();
        }

        pinnedLine.setFirstUnpinnedIndex(0);
        repository.write(solution);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Line " + pinnedLine.getId() + " was fully unpinned."
        )).build();
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

      if (!Objects.equals( (schedule.getJobs().get(0).getMaxEndTime()),loadDTO.getMaxEndDateTime() )) return false;
      if (!Objects.equals((schedule.getJobs().get(0).getIdealEndTime()),loadDTO.getIdealEndDateTime())) return false;

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

    @GET
    public PackagingSchedule get() {
        // Get the solver status before loading the solution
        // to avoid the race condition that the solver terminates between them
        SolverStatus solverStatus = solverManager.getSolverStatus(SINGLETON_SOLUTION_ID);
        PackagingSchedule schedule = repository.read();
        if (schedule == null) {
            throw new WebApplicationException("No schedule loaded", Response.Status.NOT_FOUND);
        }
        schedule.setSolverStatus(solverStatus);
        return schedule;
    }

    @GET
    @Path("lines")
    public Map<String,String> getLines() {
        if(loadData==null){
            throw new WebApplicationException("No data loaded", Response.Status.NOT_FOUND);
        }
        return loadData.getLinesIdWithNamesMap();
    }

    @POST
    @Path("solve")
    public void solve() {
        currentSolverSolution = solverManager.solveBuilder()
                .withProblemId(SINGLETON_SOLUTION_ID)
                .withProblemFinder(id -> repository.read())
                .withBestSolutionConsumer(schedule -> {
                    SolutionPostProcessor.sortJobsByNp(schedule);
                    repository.write(schedule);
                })
                .run();
    }

    @POST
    @Path("export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export() {
        try {
            PackagingSchedule schedule;

            if (currentSolverSolution != null) {
                schedule = currentSolverSolution.getFinalBestSolution();
            } else {
                schedule = repository.read();
            }
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

    @POST
    @Path("saveToDb")
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveToDb() {
        JsonExporter jsonExporter = new JsonExporter(dbUrl);
        try {
            PackagingSchedule bestSolution = currentSolverSolution.getFinalBestSolution();
            jsonExporter.export(bestSolution);
            return Response.ok(Map.of("message", "Saved to DB successfully")).build();
        } catch (Exception e) {
            return Response.serverError().entity("Save error: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("removeSolution")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSolutionByDate() {

        if (this.date == null || this.date.isBlank()) {
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
                    "message", "Solution removed for date: " + this.date + " (rows affected: " + updatedRows + ")"
            )).build();

        } catch (SQLException e) {
            return Response.serverError()
                    .entity(Map.of("status", "error", "message", "SQL error: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("analyze")
    public ScoreAnalysis<HardMediumSoftLongScore> analyze(@QueryParam("fetchPolicy") ScoreAnalysisFetchPolicy fetchPolicy) {
        PackagingSchedule problem = repository.read();
        return fetchPolicy == null ? solutionManager.analyze(problem) : solutionManager.analyze(problem, fetchPolicy);
    }

    @POST
    @Path("stopSolving")
    public void stopSolving() {
        solverManager.terminateEarly(SINGLETON_SOLUTION_ID);

    }

    public File exportTimeCompare(String date, PackagingSchedule solution) {
        ExcelExporter exporter = new ExcelExporter(dbLabelingUrl, date, solution.getJobs());
        return exporter.getExportedFile();

    }
}