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
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.dto.MaintenanceRequestDTO;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.acme.foodpackaging.sql.SqlQueries.DELETE_SOLUTION_JSON;
import static org.acme.foodpackaging.sql.SqlQueries.INSERT_PDAY;

@Path("schedule")
public class PackagingScheduleResource {

    // Атомарный флаг занятости решателя
    private final AtomicBoolean solverBusy = new AtomicBoolean(false);

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

    MaintenanceRequestDTO maintenanceRequestDTO;

    @ConfigProperty(name = "dbLabeling.url")
    String dbLabelingUrl;

    @ConfigProperty(name = "db.url")
    String dbUrl;

    String date;

    LoadDTO ldto;

    @POST
    @Path("load")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response load(LoadDTO loadDTO) {

        if (solverBusy.get()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Решатель сейчас занят другим пользователем. Попробуйте позже."))
                    .build();
        }


        LocalDate startDate = loadDTO.getStartDate();
        this.date = startDate.toString();

        try {
            if (!loadDTO.getFindSolvedInDb()) {
                createNewSchedule(loadDTO);
                return Response.ok(Map.of(
                        "message", "New schedule generated (forced) for date: " + startDate
                )).build();
            }

            PackagingSchedule schedule = tryImportScheduleFromDb(startDate);

            if (schedule != null && isScheduleCompatible(schedule, loadDTO)) {
                solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_SHADOW_VARIABLES_ONLY);
                repository.write(schedule);
                return Response.ok(Map.of(
                        "message", "Saved schedule imported for date: " + startDate
                )).build();
            }

            createNewSchedule(loadDTO);
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

    private void createNewSchedule(LoadDTO loadDTO) {
        loadData.loadDataByDate(
                loadDTO.getStartDate(),
                loadDTO.getEndDate(),
                loadDTO.getIdealEndDateTime(),
                loadDTO.getMaxEndDateTime(),
                loadDTO.toLineStartDateTimeMap()
        );
    }

    @POST
    @Path("loadpday")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loadpday(LoadDTO loadDTO) {
        if (solverBusy.get()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Решатель сейчас занят другим пользователем. Попробуйте позже."))
                    .build();
        }

        LocalDate startDate = loadDTO.getStartDate();
        this.date = startDate.toString();
        LocalDate endDate = loadDTO.getEndDate();
        ldto = loadDTO;

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
            loadData.updatePDay(ldto.getStartDate(), ldto.getEndDate(), mapsnpz);

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

        boolean sameLine = fromLine.getId().equals(toLine.getId());

        int fromIndex = request.getFromIndex();
        int count = request.getCount();

        List<Job> jobs = fromLine.getJobs();
        int fromEnd = Math.min(fromIndex + count, jobs.size());
        
        if (fromIndex < 0 || fromIndex >= jobs.size() || fromIndex >= fromEnd) {
            return Response.ok(Map.of("status", "success", "message", "Nothing to move")).build();
        }

        if (!sameLine) {
            for (int i = fromIndex; i < fromEnd; i++) {
                Job job = jobs.get(i);
                String productType = job.getProduct().getType();

                Integer duration = job.getLineSpeeds()
                        .getOrDefault(toLine.getId(), Map.of())
                        .get(productType);

                if (duration == null || duration == 0) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of(
                                    "error",
                                    "Cannot move job \"" + job.getName() +
                                            "\" to line \"" + toLine.getName() + "\": product type unsupported"
                            ))
                            .build();
                }
            }
        }

        // ----- no-op: вставка внутрь того же диапазона -----
        if (sameLine
                && request.getInsertIndex() >= fromIndex
                && request.getInsertIndex() <= fromEnd) {
            return Response.ok(Map.of("status", "success", "message", "No-op")).build();
        }

        // ---- Выполняем перенос -----
        List<Job> moved = moveSubList(fromLine, fromIndex, count, toLine, request.getInsertIndex());

        if (moved.isEmpty()) {
            return Response.ok(Map.of("status", "success", "message", "No jobs moved")).build();
        }

        // ---- фиксация (ваши методы) -----
        fixLineJobs(fromLine);
        if (!sameLine) fixLineJobs(toLine);

        fixPinIndexes(schedule);
        SolutionPostProcessor.sortJobsByNp(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.write(schedule);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Jobs moved successfully"
        )).build();
    }
    /**
     * Перемещает подсписок из одного списка в другой и возвращает перемещённые задачи
     */
    private List<Job> moveSubList(Line fromLine, int fromIndex, int count,
                                  Line toLine, int insertIndex) {

        boolean sameLine = fromLine.getId().equals(toLine.getId());

        List<Job> fromJobs = new ArrayList<>(fromLine.getJobs());
        List<Job> toJobs = sameLine ? fromJobs : new ArrayList<>(toLine.getJobs());

        int fromEnd = Math.min(fromIndex + count, fromJobs.size());
        if (fromIndex < 0 || fromIndex >= fromJobs.size() || fromIndex >= fromEnd) {
            return Collections.emptyList();
        }

        List<Job> jobsToMove = new ArrayList<>();
        for (int i = fromIndex; i < fromEnd; i++) {
            jobsToMove.add(fromJobs.get(i));
        }

        for (int i = 0; i < jobsToMove.size(); i++) {
            fromJobs.remove(fromIndex);
        }

        if (sameLine && insertIndex > fromIndex) {
            insertIndex -= jobsToMove.size();
        }

        insertIndex = Math.max(0, Math.min(insertIndex, toJobs.size()));

        List<Job> newToJobs = new ArrayList<>();

        for (int i = 0; i < toJobs.size(); i++) {
            if (i == insertIndex) {
                newToJobs.addAll(jobsToMove);
            }
            newToJobs.add(toJobs.get(i));
        }

        if (insertIndex == toJobs.size()) {
            newToJobs.addAll(jobsToMove);
        }

        fromLine.setJobs(fromJobs);
        if (!sameLine) {
            toLine.setJobs(newToJobs);
        } else {
            fromLine.setJobs(newToJobs);
        }

        return jobsToMove;
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
    @Path("maintenance")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addMaintenance(MaintenanceRequestDTO request) {
        PackagingSchedule schedule = repository.read();
        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No schedule loaded"))
                    .build();
        }
        int idx = request.getInsertIndex();

        if (idx < 0 || idx >= schedule.getJobs().size()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid insertIndex: " + idx))
                    .build();
        }

        Job baseJob = schedule.getJobs().get(idx);

        Line line = schedule.getLines().stream()
                .filter(l -> l.getId().equals(request.getLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + request.getLineId()));

        Product maintenanceProduct = schedule.getProducts().stream()
                .filter(p -> "MAINTENANCE".equalsIgnoreCase(p.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Maintenance product with id='MAINTENANCE' not found"));

        Job job = new Job(
                "MAINTENANCE",
                request.getName(),
                maintenanceProduct,
                Duration.ofMinutes(90),   // duration
                LocalDateTime.of(2025, 10, 4, 8, 0),                // minStartTime
                LocalDateTime.of(2025, 10, 5, 2, 0),                // idealEndTime
                LocalDateTime.of(2025, 10, 5, 7, 0),               // maxEndTime
                1,                                                  // priority (1 → 10)
                true,                                               // pinned
                null,                                               // startCleaningDateTime
                null                                                // startProductionDateTime
        );

        job.setLineSpeeds(baseJob.getLineSpeeds());
        job.setMaintenance(true);
        schedule.getJobs().add(job);

        repository.write(schedule);

        return Response.ok(Map.of(
                "status", "success",
                "message", "Maintenance job added successfully",
                "lineId", line.getId()
        )).build();
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
        // Get the solver status before loading the solution1
        // to avoid the race condition that the solver terminates between them
        SolverStatus solverStatus = solverManager.getSolverStatus(SINGLETON_SOLUTION_ID);
        PackagingSchedule schedule = repository.read();
        if (schedule == null) {
            throw new WebApplicationException("No schedule loaded", Response.Status.NOT_FOUND);
        }
        schedule.setSolverStatus(solverStatus);

        //Если планировщик сам перестал считать то принудительно освобождаем планировщик
        if(solverStatus.name().equals("NOT_SOLVING") && solverBusy.get()){
            solverBusy.set(false);
        }

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
    public Response solve() {

        // Устанавливаем блокировку
        if (!solverBusy.compareAndSet(false, true)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Решатель сейчас занят другим пользователем. Попробуйте позже."))
                    .build();
        }

        try {
            currentSolverSolution = solverManager.solveBuilder()
                    .withProblemId(SINGLETON_SOLUTION_ID)
                    .withProblemFinder(id -> repository.read())
                    .withBestSolutionConsumer(schedule -> {
                        repository.write(schedule);
                    })
                    .run();
        } catch (Exception e){
            solverBusy.set(false);
        }

        return Response.ok().build();
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
        PackagingSchedule finalSchedule = repository.read();
        SolutionPostProcessor.sortJobsByNp(finalSchedule);
        repository.write(finalSchedule);
        solverBusy.set(false);
    }

    public File exportTimeCompare(String date, PackagingSchedule solution) {
        ExcelExporter exporter = new ExcelExporter(dbLabelingUrl, date, solution.getJobs());
        return exporter.getExportedFile();

    }
}