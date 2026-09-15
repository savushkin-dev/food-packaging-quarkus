package org.acme.foodpackaging.rest.solution;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.response.solution.FrontendDataResponse;
import org.acme.foodpackaging.exception.service.FrontDataSerializationException;
import org.acme.foodpackaging.repository.PackagingScheduleRepository;
import org.jboss.logging.Logger;

import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.service.lines.LineService;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Path("schedule")
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ScheduleQueryResource {

    private static final Logger LOG = Logger.getLogger(ScheduleQueryResource.class);

    private final SolverManager<PackagingSchedule, String> solverManager;
    private final PackagingScheduleRepository repository;
    private final SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager;
    private final LineService lineService;
    private final ScheduleSessionService scheduleSessionService;
    private final ObjectMapper objectMapper;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PackagingSchedule get(@HeaderParam("X-Session-Id") String sessionId) {
        SolverStatus solverStatus = solverManager.getSolverStatus(scheduleSessionService.getProblemId(sessionId));
        PackagingSchedule schedule = scheduleSessionService.requireScheduleForRead(sessionId);
        schedule.setSolverStatus(solverStatus);
        return schedule;
    }

    @GET
    @Path("frontData")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFrontendData(@HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule schedule = scheduleSessionService.requireScheduleForRead(sessionId);
        // Статус солвера всегда берется напрямую из solverManager, а не из закэшированного
        // поля на schedule: оно проставляется только внутри get() и стирается новым
        // клоном решения из withBestSolutionConsumer, из-за чего frontData мог отдавать
        // solverStatus = null (undefined на фронте) в зависимости от гонки запросов.
        SolverStatus solverStatus = solverManager.getSolverStatus(scheduleSessionService.getProblemId(sessionId));
        FrontendDataResponse response = new FrontendDataResponse(
                schedule.getJobs(),
                schedule.getLines(),
                schedule.getScore(),
                solverStatus);

        // Временная диагностика. Во время активного солвинга solver-поток может
        // мутировать shadow-переменные (line, previousJob, nextJob) и не клонируемые
        // Timefold-ом поля PackagingSchedule ровно в момент, когда Jackson обходит
        // этот же граф объектов, сериализуя ответ. В таком случае RESTEasy перехватывает
        // сбой сериализации сам, в обход наших ExceptionMapper-ов, и отдаёт клиенту
        // только голый текст "Not able to deserialize data provided." без каких-либо
        // деталей (см. FrontDataSerializationException). Поэтому сериализуем ответ сами:
        // это позволяет поймать сбой здесь, залогировать полный стектрейс с контекстом
        // и вернуть клиенту нормальную структурированную ошибку вместо этого текста.
        String json;
        try {
            json = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            long jobsWithoutLine = schedule.getJobs() == null ? -1
                    : schedule.getJobs().stream().filter(job -> job.getLine() == null).count();
            LOG.errorf(e,
                    "frontData: сбой сериализации ответа. sessionId=%s, problemId=%s, solverStatus=%s, "
                            + "jobs=%d, jobsWithoutLine=%d, lines=%d",
                    sessionId,
                    scheduleSessionService.getProblemId(sessionId),
                    solverStatus,
                    schedule.getJobs() == null ? -1 : schedule.getJobs().size(),
                    jobsWithoutLine,
                    schedule.getLines() == null ? -1 : schedule.getLines().size());
            throw new FrontDataSerializationException(
                    "Не удалось сериализовать frontData во время solving: " + e.getMessage(), e);
        }

        return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("dailyProductions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dailyProductions(
            @HeaderParam("X-Session-Id") String sessionId,
            @QueryParam("shiftStart") String shiftStartParam) {

        if (shiftStartParam == null || shiftStartParam.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, "shiftStart query parameter is required"))
                    .build();
        }

        LocalDateTime shiftStart;
        try {
            shiftStart = LocalDateTime.parse(shiftStartParam);
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(ApiFields.ERROR, "shiftStart must be in ISO format (yyyy-MM-ddTHH:mm:ss)"))
                    .build();
        }

        PackagingSchedule solution = repository.readForSession(sessionId);

        if (solution == null) {
            return scheduleSessionService.noScheduleLoadedResponse();
        }

        Map<String, Object> productions = lineService.calculateLineProductions(solution.getLines(), shiftStart);

        return Response.ok(productions).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("analyze")
    public ScoreAnalysis<HardMediumSoftLongScore> analyze(
            @QueryParam("fetchPolicy") ScoreAnalysisFetchPolicy fetchPolicy,
            @HeaderParam("X-Session-Id") String sessionId) {
        PackagingSchedule problem = scheduleSessionService.requireScheduleForRead(sessionId);
        return fetchPolicy == null ? solutionManager.analyze(problem) : solutionManager.analyze(problem, fetchPolicy);
    }
}
