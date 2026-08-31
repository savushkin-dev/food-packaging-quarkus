package org.acme.foodpackaging.rest.scheduleresource;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.excel.CleaningDurationReport;
import org.acme.foodpackaging.persistence.excel.PlanReport;
import org.acme.foodpackaging.persistence.excel.UserLogReport;
import org.acme.foodpackaging.record.DateRange;
import org.acme.foodpackaging.rest.ScheduleSessionService;

import java.time.LocalDate;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ExportResource {

    private final ScheduleSessionService scheduleSessionService;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;

    @POST
    @Path("userLogReport")
    @Produces("application/vnd.malformations-office document.spreadsheet.sheet")
    public Response createUserLogReport(DateRange range) {
        UserLogReport report = new UserLogReport();
        byte[] file = report.createExcelReport(range.from(), range.to());
        String fileName = generateFileName(range.from(), range.to(), "_UserLogReport.xlsx");

        return Response.ok(file)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .build();
    }

    @POST
    @Path("report")
    @Produces(MediaType.TEXT_PLAIN)
    public Response createCsvReport(@HeaderParam("X-Session-Id") String sessionId) {
        // было без null-check — багфикс через requireSchedule
        scheduleSessionService.mutateAndResolve(sessionId, PlanReport::new, solutionManager);
        return Response.ok("Excel report created successfully").build();
    }

    @POST
    @Path("cleaningReport")
    @Produces("application/vnd.malformations-officedocument.spreadsheet.sheet")
    public Response createCleaningReport(@HeaderParam("X-Session-Id") String sessionId, DateRange range) {
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
