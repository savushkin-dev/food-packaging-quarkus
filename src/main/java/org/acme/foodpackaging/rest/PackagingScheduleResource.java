package org.acme.foodpackaging.rest;

import ai.timefold.solver.core.api.solver.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.bootstrap.LoadData;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.persistence.ExcelExporter;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_JOBS;
import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LINES;

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
        try {

            loadData.loadDataByDate(loadDTO.getStartDate(), loadDTO.getEndDate(),
                    loadDTO.getIdealEndDateTime(), loadDTO.getMaxEndDateTime(), loadDTO.toLineStartDateTimeMap());
            date = String.valueOf(loadDTO.getStartDate());

            return Response.ok().entity(Map.of("message", "Data loaded successfully for date: " + loadDTO.getStartDate())).build();
        } catch (DateTimeParseException e) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid date format. Please use YYYY-MM-DD"))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Failed to load data: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    public PackagingSchedule get() {
        // Get the solver status before loading the solution
        // to avoid the race condition that the solver terminates between them
        SolverStatus solverStatus = solverManager.getSolverStatus(SINGLETON_SOLUTION_ID);
        PackagingSchedule schedule = repository.read();
        schedule.setSolverStatus(solverStatus);
        return schedule;
    }

    @GET
    @Path("lines")
    public Map<Integer,String> getLines() {
        Map<Integer,String> lines = new HashMap<>();
        ResultSet resultSet = null;
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();) {
             resultSet = statement.executeQuery(LOAD_LINES);
            int index = 1;
             while (resultSet.next()) {
                String krc = resultSet.getString("krc");
                lines.put(index, krc);
                ++index;
             }
        }
         catch (SQLException e) {
             throw new RuntimeException("Failed to load lines from DB", e);
         }
        return lines;
    }

    @POST
    @Path("solve")
    public void solve() {
        currentSolverSolution = solverManager.solveBuilder()
                .withProblemId(SINGLETON_SOLUTION_ID)
                .withProblemFinder(id -> repository.read())
                .withBestSolutionConsumer(schedule -> repository.write(schedule))
                .run();
    }

    @POST
    @Path("export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export() {
        if (currentSolverSolution != null) {
            try {
                PackagingSchedule bestSolution = currentSolverSolution.getFinalBestSolution();
                if (bestSolution != null) {
                    File excelFile = exportTimeCompare(date, bestSolution);
                    if (excelFile != null && excelFile.exists()) {
                        byte[] fileContent = Files.readAllBytes(excelFile.toPath());
                        return Response.ok(fileContent)
                                .header("Content-Disposition", "attachment; filename=\"" + excelFile.getName() + "\"")
                                .build();
                    } else {
                        return Response.serverError().entity("The Excel file was not created").build();
                    }
                } else {
                    return Response.status(Response.Status.NO_CONTENT)
                            .entity("Best solution is null — export skipped.")
                            .build();
            } } catch (InterruptedException | ExecutionException | IOException e) {
        return Response.serverError().entity("Export error: " + e.getMessage()).build();
    }
} else {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Current solver solution is null")
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