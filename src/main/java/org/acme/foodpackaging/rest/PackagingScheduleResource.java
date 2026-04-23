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
import org.acme.foodpackaging.dto.*;
import org.acme.foodpackaging.persistence.*;
import org.acme.foodpackaging.persistence.excel.CleaningDurationReport;
import org.acme.foodpackaging.persistence.excel.PlanReport;
import org.acme.foodpackaging.persistence.excel.UserLogReport;
import org.acme.foodpackaging.persistence.upload.*;
import org.acme.foodpackaging.record.*;
import org.acme.foodpackaging.repository.solution.PlrPlanRepository;
import org.acme.foodpackaging.scheduleoperations.*;
import org.acme.foodpackaging.service.builder.*;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.jobs.*;

import java.util.*;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@Path("schedule")
@ApplicationScoped
public class PackagingScheduleResource {

    private final PackagingScheduleRepository repository;
    private final SolverManager<PackagingSchedule, String> solverManager;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final MaintenanceJob maintenanceJob;
    private final JobService jobService;
    private final MoveJobsService moveJobsService;
    private final SortByNpService sortByNpService;
    private final PinService pinService;
    private final ScheduleBuilder scheduleBuilder;
    private final ScheduleBuilderByVersion builderByVersion;
    private final LoadDataService loadDataService;
    private final UploadDataService uploadDataService;
    private final JobRefreshService jobRefreshService;
    private final JobSaveService jobSaveService;
    private final SolutionVersionExportService exportService;
    private final JobInfoService jobInfoService;
    private final AlignSolutionService alignSolutionService;
    private final PlrPlanRepository plrPlanRepository;

    @Inject
    public PackagingScheduleResource(
            PackagingScheduleRepository repository, SolverManager<PackagingSchedule, String> solverManager,
            SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager, MaintenanceJob maintenanceJob,
            JobService jobService, MoveJobsService moveJobsService, SortByNpService sortByNpService, PinService pinService,
            ScheduleBuilder scheduleBuilder, ScheduleBuilderByVersion builderByVersion, LoadDataService loadDataService,
            UploadDataService uploadDataService, JobRefreshService jobRefreshService, JobSaveService jobSaveService,
            SolutionVersionExportService exportService, JobInfoService jobInfoService, AlignSolutionService alignSolutionService, PlrPlanRepository plrPlanRepository
    ) {
        this.repository = repository;
        this.solverManager = solverManager;
        this.solutionManager = solutionManager;
        this.maintenanceJob = maintenanceJob;
        this.jobService = jobService;
        this.moveJobsService = moveJobsService;
        this.sortByNpService = sortByNpService;
        this.pinService = pinService;
        this.scheduleBuilder = scheduleBuilder;
        this.builderByVersion = builderByVersion;
        this.loadDataService = loadDataService;
        this.uploadDataService = uploadDataService;
        this.jobRefreshService = jobRefreshService;
        this.jobSaveService = jobSaveService;
        this.exportService = exportService;
        this.jobInfoService = jobInfoService;
        this.alignSolutionService = alignSolutionService;
        this.plrPlanRepository = plrPlanRepository;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PackagingSchedule get(@HeaderParam("X-Session-Id") String sessionId) {

        SolverStatus solverStatus = solverManager.getSolverStatus(getProblemId(sessionId));
        PackagingSchedule schedule = repository.readForSession(sessionId);
        if (schedule == null) {
            throw new WebApplicationException(ApiFields.NO_SCHEDULE_LOADED, Response.Status.NOT_FOUND);
        }
        schedule.setSolverStatus(solverStatus);
        return schedule;
    }

    @GET
    @Path("frontData")
    @Produces(MediaType.APPLICATION_JSON)
    public FrontendDataWrapper getFrontendData(@HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = repository.readForSession(sessionId);
        if (schedule == null) {
            throw new WebApplicationException("No schedule loaded", Response.Status.NOT_FOUND);
        }
        return new FrontendDataWrapper(
                schedule.getJobs(),
                schedule.getLines(),
                schedule.getScore(),
                schedule.getSolverStatus()
        );
    }

    @GET
    @Path("lines")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getLines() {
        if (loadDataService == null) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }
        return loadDataService.getLines();
    }

    @GET
    @Path("serviceTypes")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<Integer, String> getMaintenanceTypes() {
        if (loadDataService == null) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }
        return loadDataService.getMaintenanceTypes();
    }

    @POST
    @Path("findCameraFact")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findCameraFact(@HeaderParam("X-Session-Id") String sessionId, PlaceFactRequest placeFactRequest) {
        PackagingSchedule schedule = repository.readForSession(sessionId);
        schedule = jobInfoService.findCameraFact(schedule, placeFactRequest.getSnpz());
        repository.writeForSession(sessionId, schedule);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, ""
        )).build();
    }

    @POST
    @Path("versionsByDate")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getPlanVersions(LoadRequest loadDTO) {
        return plrPlanRepository.findDistinctVersionsByDate(loadDTO.getStartDate().atStartOfDay().toLocalDate());
    }

    @POST
    @Path("findPlaceFact")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findFactPlace(@HeaderParam("X-Session-Id") String sessionId, PlaceFactRequest placeFactRequest) {
        PackagingSchedule schedule = repository.readForSession(sessionId);
        schedule = jobInfoService.findFactPlace(schedule, placeFactRequest.getSnpz());
        repository.writeForSession(sessionId, schedule);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, ""
        )).build();
    }

    @POST
    @Path("refreshData")
    @Produces(MediaType.APPLICATION_JSON)
    public Response refreshData() {
        loadDataService.refresh();
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Data refreshed successfully from database"
        )).build();
    }

    @POST
    @Path("delayNote")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delayNote(@HeaderParam("X-Session-Id") String sessionId, DelayNoteRequest request) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        jobService.writeDelayNote(request, schedule);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Note is written").build();
    }

    @POST
    @Path("alignPlan")
    @Produces(MediaType.APPLICATION_JSON)
    public Response alignPlan(@HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }
        alignSolutionService.alignByFactDuration(schedule);
        alignSolutionService.alignLineStartByFact(schedule);
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, ApiFields.REFRESH_OK
        )).build();
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

    @POST
    @Path("init")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response init(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        InitData data =  scheduleBuilder.buildSchedule(loadDTO.getStartDate());
        PackagingSchedule schedule = data.schedule();
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        if (loadDataService == null) {
                throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
            }

            return Response.ok(data.jobsFromDbRow()).build();
    }

    @POST
    @Path("initVersion")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response initVersion(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule solution =  builderByVersion.init(loadDTO.getStartDate(), loadDTO.getVersion());
        solution.setVersion(loadDTO.getVersion());
        solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, solution);

        if (loadDataService == null) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Solution version imported from json"
        )).build();
    }

    @POST
    @Path("/selection")
    public Response applySelection(@HeaderParam("X-Session-Id") String sessionId, JobSelection dto) {

        PackagingSchedule solution = repository.readForSession(sessionId);
        solution.getOverloadedIds().clear();

        PackagingSchedule updatedSchedule = jobRefreshService.applySelection(dto.selection(),
                solution);

        solutionManager.update(updatedSchedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId,updatedSchedule);

        return Response.ok().build();
    }

    @POST
    @Path("lineStart")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLineStartTime(@HeaderParam("X-Session-Id") String sessionId, TimeUpdate request) {

        PackagingSchedule solution = repository.readForSession(sessionId);

        if (solution == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }
        Line line = findLineById(solution, request.getLineId());
        if(line.getJobs().isEmpty()) {
            setLineStartDateTime(line, request.getStartLineDateTime());

            solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
            repository.writeForSession(sessionId, solution);
            return Response.ok(Map.of(
                    ApiFields.STATUS, ApiFields.SUCCESS,
                    ApiFields.SESSION_ID, sessionId,
                    ApiFields.MESSAGE, "Line start time updated"
            )).build();
        }
        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Line has jobs. Start time is not updated"
        )).build();
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
                ApiFields.MESSAGE, "Line end time updated"
        )).build();
    }

    @POST
    @Path("updateOrderList")
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateOrderList(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
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
    @Path("sortRange")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sortRangeByNp(SortRangeRequest request, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        sortByNpService.sortRangeByNp(schedule, request);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok(Map.of(ApiFields.STATUS, ApiFields.SUCCESS, ApiFields.MESSAGE, "Jobs sorted successfully")).build();
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
                ApiFields.MESSAGE, "Solving started"
        )).build();
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

        DowntimeData response = getDowntimeData(repository.readForSession(sessionId));

        return Response.ok(response).build();
    }
    /**
     * Перемещение задач на линиях
     */
    @POST
    @Path("moveJobs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response moveJobs(MoveJobsRequest request, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        PackagingSchedule result = moveJobsService.moveJobs(schedule, request);

        solutionManager.update(result, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, result);

        return Response.ok(Map.of(ApiFields.STATUS, ApiFields.SUCCESS, ApiFields.MESSAGE, "Jobs moved successfully")).build();
    }
    /**
     * Суточная мойка линий
     */
    @POST
    @Path("dailyCleaning")
    @Produces(MediaType.TEXT_PLAIN)
    public Response dailyCleaning(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }
        maintenanceJob.addDailyFullCleaning(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Cleanings added successfully").build();
    }

    /**
     * Операции для сервисной работы на линии
     */
    @POST
    @Path("maintenance")
    public Response addMaintenance(MaintenanceRequest request,
                                   @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);
        PackagingSchedule updated;
        if (schedule == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }
            if(request.isUpdateLineMode()){
                if(request.getMaintenanceTypeId()!=null) {
                    updated = maintenanceJob.updateMaintenanceType(schedule, request);
                }
                else{
                    updated = maintenanceJob.updateDuration(schedule,request);
                }
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
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.MESSAGE, "Maintenance job added",
                ApiFields.LINE_ID, request.getLineId()
        )).build();
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
                ApiFields.MESSAGE, "Line " + line.getId() + " updated successfully."
        )).build();
    }

    /**
     * Сохраняет план
     */
    @POST
    @Path("save")
    public Response save(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule bestSolution = repository.readForSession(sessionId);
        if (bestSolution == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        jobSaveService.saveJobsByType(bestSolution);
        DowntimeData response = getDowntimeData(repository.readForSession(sessionId));

        return Response.ok(response).build();
    }

    /**
     * Сохраняет план в json  определенной версии
     */
    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("saveVersion")
    public Response saveVersion(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule bestSolution = repository.readForSession(sessionId);
        if (bestSolution == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of(ApiFields.ERROR, ApiFields.NO_SCHEDULE_LOADED))
                    .build();
        }

        if(bestSolution.getVersion() == null && loadDTO.getVersion() == null){
            bestSolution.setVersion("V1");
        }
        else {
            bestSolution.setVersion(loadDTO.getVersion());
        }
        exportService.export(bestSolution, bestSolution.getVersion());
        return Response.ok(Map.of(ApiFields.MESSAGE, "Saved to PlrPLan successfully")).build();
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

    // ========== Генерация Excel отчетов ==========
    @POST
    @Path("userLogReport")
    @Produces(MediaType.TEXT_PLAIN)
    public Response createUserLogReport(DateRange range) {

        new UserLogReport(range.from(), range.to());

        return Response.ok("UserLog Excel report created successfully").build();
    }

    @POST
    @Path("report")
    @Produces(MediaType.TEXT_PLAIN)
    public Response createCsvReport(@HeaderParam("X-Session-Id") String sessionId) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        new PlanReport(schedule);

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Excel report created successfully").build();
    }

    @POST
    @Path("cleaningReport")
    @Produces(MediaType.TEXT_PLAIN)
    public Response createCsvCleaningReport(@HeaderParam("X-Session-Id") String sessionId, DateRange range) {

        PackagingSchedule schedule = repository.readForSession(sessionId);

        new CleaningDurationReport(schedule, range.from(), range.to());

        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);

        return Response.ok("Excel report created successfully").build();
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private String getProblemId(String sessionId) {
        return sessionId != null ? sessionId : "default";
    }
}