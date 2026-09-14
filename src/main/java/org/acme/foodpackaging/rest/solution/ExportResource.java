package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.excel.CleaningDurationReport;
import org.acme.foodpackaging.excel.PlanReport;
import org.acme.foodpackaging.excel.UserLogReport;
import org.acme.foodpackaging.dto.request.solution.DateRangeRequest;
import org.acme.foodpackaging.rest.ApiFields;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Path("schedule/reports")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ExportResource {

    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ScheduleSessionService scheduleSessionService;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;

    @GET
    @Path("userLog")
    @Produces(XLSX_MEDIA_TYPE)
    public Response createUserLogReport(@QueryParam("from") String fromParam, @QueryParam("to") String toParam) {
        if (fromParam == null || fromParam.isBlank() || toParam == null || toParam.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, "'from' and 'to' query parameters are required"))
                    .build();
        }

        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fromParam);
            to = LocalDate.parse(toParam);
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, "'from' and 'to' must be in ISO format (yyyy-MM-dd)"))
                    .build();
        }

        UserLogReport report = new UserLogReport();
        byte[] file = report.createExcelReport(from, to);
        String fileName = generateFileName(from, to, "_UserLogReport.xlsx");

        return Response.ok(file)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .build();
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createCsvReport(@HeaderParam("X-Session-Id") String sessionId) {
        scheduleSessionService.mutateAndResolve(sessionId, PlanReport::new, solutionManager);
        return Response.ok("Excel report created successfully").build();
    }

    @POST
    @Path("cleaning")
    @Produces(XLSX_MEDIA_TYPE)
    public Response createCleaningReport(@HeaderParam("X-Session-Id") String sessionId, DateRangeRequest range) {
        PackagingSchedule schedule = scheduleSessionService.requireSchedule(sessionId);

        CleaningDurationReport report = new CleaningDurationReport();
        byte[] file = report.createExcelReport(schedule, range.from(), range.to());

        scheduleSessionService.resolve(sessionId, solutionManager);

        String fileName = generateFileName(range.from(), range.to(), "_CleaningReport.xlsx");

        return Response.ok(file)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .build();
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private String generateFileName(LocalDate from, LocalDate to, String postfixString) {
        return from + "—" + to + "_" + postfixString + ".xlsx";
    }
}
