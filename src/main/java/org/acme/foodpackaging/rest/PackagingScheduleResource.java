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
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.dto.MoveJobsRequestDTO;
import org.acme.foodpackaging.dto.PinRequestDTO;
import org.acme.foodpackaging.dto.*;
import org.acme.foodpackaging.persistence.*;
import org.acme.foodpackaging.scheduleOperations.MaintenanceJob;
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

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleFixUtils.*;
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
                PackagingSchedule createdSchedule = createNewSchedule(loadDTO);
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

            PackagingSchedule newSchedule = createNewSchedule(loadDTO);
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

    private PackagingSchedule createNewSchedule(LoadDTO loadDTO) {
        return loadData.loadDataByDate(
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
        Line line = schedule.getLines().stream()
                .filter(l -> l.getId().equals(request.getLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + request.getLineId()));

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

        reorderJobsByProductNp(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Sorted successfully").build();
    }

    private void reorderJobsByProductNp(PackagingSchedule schedule) {

        Map<Product, Deque<Job>> pools = new HashMap<>();
        for (Job job : schedule.getJobs()) {
            if (!job.isMaintenance()) {
                pools.computeIfAbsent(job.getProduct(), p -> new ArrayDeque<>()).add(job);
            }
        }

        for (Deque<Job> deque : pools.values()) {
            List<Job> list = new ArrayList<>(deque);
            list.sort(Comparator.comparing(Job::getNp));
            deque.clear();
            deque.addAll(list);
        }

        Map<Product, Integer> appearanceCounter = new HashMap<>();

        for (Line line : schedule.getLines()) {

            List<Job> original = line.getJobs();
            List<Job> newOrder = new ArrayList<>();

            Map<Product, Integer> requiredCount = new LinkedHashMap<>();
            for (Job j : original) {
                if (!j.isMaintenance()) {
                    Product p = j.getProduct();
                    requiredCount.put(p, requiredCount.getOrDefault(p, 0) + 1);
                }
            }

            Map<Product, Iterator<Job>> productIterators = new HashMap<>();
            for (Map.Entry<Product, Integer> e : requiredCount.entrySet()) {
                Product product = e.getKey();
                int cnt = e.getValue();

                Deque<Job> pool = pools.get(product);
                if (pool == null || pool.size() < cnt) {
                    throw new IllegalStateException(
                            "Not enough jobs in pool for product " + product.getName());
                }

                List<Job> portion = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) {
                    portion.add(pool.pollFirst());
                }

                int appearance = appearanceCounter.getOrDefault(product, 0);
                if ((appearance % 2) == 1) {
                    Collections.reverse(portion);
                }

                productIterators.put(product, portion.iterator());
            }

            List<Job> buffer = new ArrayList<>();
            for (Job job : original) {

                if (hadCleaningBefore(job)) {
                    if (!buffer.isEmpty()) {
                        newOrder.addAll(fillSubchainFromIterators(buffer, productIterators));
                        buffer.clear();
                    }
                }

                if (job.isMaintenance()) {
                    if (!buffer.isEmpty()) {
                        newOrder.addAll(fillSubchainFromIterators(buffer, productIterators));
                        buffer.clear();
                    }
                    newOrder.add(job);
                    continue;
                }

                buffer.add(job);
            }

            if (!buffer.isEmpty()) {
                newOrder.addAll(fillSubchainFromIterators(buffer, productIterators));
            }

            for (int i = 0; i < newOrder.size(); i++) {
                Job prev = (i > 0) ? newOrder.get(i - 1) : null;
                Job next = (i < newOrder.size() - 1) ? newOrder.get(i + 1) : null;
                Job current = newOrder.get(i);
                current.setPreviousJob(prev);
                current.setNextJob(next);
            }

            for (Product p : requiredCount.keySet()) {
                appearanceCounter.put(p, appearanceCounter.getOrDefault(p, 0) + 1);
            }

            line.setJobs(newOrder);
        }

        List<Job> allJobs = new ArrayList<>();
        for (Line line : schedule.getLines()) {
            fixLineJobs(line);
            allJobs.addAll(line.getJobs());
        }
        schedule.setJobs(allJobs);
    }

    private boolean hadCleaningBefore(Job job) {
        if (job.getStartCleaningDateTime() == null || job.getStartProductionDateTime() == null)
            return false;
        return job.getStartCleaningDateTime().isBefore(job.getStartProductionDateTime());
    }

    /**
     * Заполняет подцепочку из заранее подготовленных итераторов для продуктов на линии
     */
    private List<Job> fillSubchainFromIterators(List<Job> subchain, Map<Product, Iterator<Job>> productIterators) {
        List<Job> result = new ArrayList<>(subchain.size());
        for (Job oldJob : subchain) {
            Product p = oldJob.getProduct();
            Iterator<Job> it = productIterators.get(p);
            if (it == null || !it.hasNext()) {
                throw new IllegalStateException("No iterator or exhausted for product " + p.getName());
            }
            result.add(it.next());
        }
        return result;
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
                if (job.isMaintenance()) continue;
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

        if (sameLine
                && request.getInsertIndex() >= fromIndex
                && request.getInsertIndex() <= fromEnd) {
            return Response.ok(Map.of("status", "success", "message", "No-op")).build();
        }

        List<Job> moved = moveSubList(fromLine, fromIndex, count, toLine, request.getInsertIndex());

        if (moved.isEmpty()) {
            return Response.ok(Map.of("status", "success", "message", "No jobs moved")).build();
        }

        fixLineJobs(fromLine);
        fixPinnedJobs(fromLine);
        if (!sameLine) {
            fixLineJobs(toLine);
            fixPinnedJobs(toLine);
        }

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

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