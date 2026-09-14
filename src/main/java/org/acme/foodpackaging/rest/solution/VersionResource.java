package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.solution.LoadRequest;
import org.acme.foodpackaging.initializer.ScheduleVersionInitializer;
import org.acme.foodpackaging.repository.PackagingScheduleRepository;
import org.acme.foodpackaging.repository.solution.PlrPlanRepository;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.load.LoadDataService;
import org.acme.foodpackaging.service.upload.SolutionVersionExportService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Path("schedule/versions")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class VersionResource {

    private final PlrPlanRepository plrPlanRepository;
    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final ScheduleVersionInitializer scheduleVersionInitializer;
    private final LoadDataService loadDataService;
    private final SolutionVersionExportService exportService;
    private final ScheduleSessionService scheduleSessionService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getPlanVersions(@QueryParam("startDate") String startDateParam) {
        if (startDateParam == null || startDateParam.isBlank()) {
            throw new WebApplicationException("'startDate' query parameter is required", Response.Status.BAD_REQUEST);
        }

        LocalDate startDate;
        try {
            startDate = LocalDate.parse(startDateParam);
        } catch (DateTimeParseException e) {
            throw new WebApplicationException("'startDate' must be in ISO format (yyyy-MM-dd)", Response.Status.BAD_REQUEST);
        }

        return plrPlanRepository.findDistinctVersionsByDate(startDate);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response initVersion(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {

        if (!loadDataService.isLoaded()) {
            throw new WebApplicationException(ApiFields.NO_DATA_LOADED, Response.Status.NOT_FOUND);
        }

        PackagingSchedule solution = scheduleVersionInitializer.initSchedule(loadDTO.startDate(), loadDTO.version());
        solution.setVersion(loadDTO.version());
        solutionManager.update(solution, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, solution);

        return Response.ok(Map.of(
                ApiFields.STATUS, ApiFields.SUCCESS,
                ApiFields.SESSION_ID, sessionId,
                ApiFields.MESSAGE, "Solution version imported from json")).build();
    }

    /**
     * Сохраняет план в json определенной версии
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveVersion(LoadRequest loadDTO, @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule bestSolution = scheduleSessionService.requireScheduleForRead(sessionId);

        if (bestSolution.getVersion() == null && loadDTO.version() == null) {
            bestSolution.setVersion("V1");
        } else {
            bestSolution.setVersion(loadDTO.version());
        }
        exportService.export(bestSolution, bestSolution.getVersion());
        return Response.ok(Map.of(ApiFields.MESSAGE, "Saved to PlrPLan successfully")).build();
    }
}
