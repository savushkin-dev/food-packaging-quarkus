package org.acme.foodpackaging.rest;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;

import java.util.function.Consumer;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ScheduleSessionService {

    private final PackagingScheduleRepository repository;

    public String getProblemId(String sessionId) {
        return sessionId != null ? sessionId : "default";
    }

    public PackagingSchedule requireSchedule(String sessionId) {
        return requireSchedule(sessionId, Response.Status.BAD_REQUEST);
    }

    public PackagingSchedule requireScheduleForRead(String sessionId) {
        return requireSchedule(sessionId, Response.Status.NOT_FOUND);
    }

    private PackagingSchedule requireSchedule(String sessionId, Response.Status status) {
        PackagingSchedule schedule = repository.readForSession(sessionId);
        if (schedule == null) {
            throw new WebApplicationException(ApiFields.NO_SCHEDULE_LOADED, status);
        }
        return schedule;
    }

    public void mutate(String sessionId, Consumer<PackagingSchedule> mutation) {
        PackagingSchedule schedule = requireSchedule(sessionId);
        mutation.accept(schedule);
        repository.writeForSession(sessionId, schedule);
    }

    public void mutateAndResolve(String sessionId, Consumer<PackagingSchedule> mutation,
                                 SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager) {
        PackagingSchedule schedule = requireSchedule(sessionId);
        mutation.accept(schedule);
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);
    }

    public void resolve(String sessionId, SolutionManager<PackagingSchedule, HardMediumSoftLongScore> solutionManager) {
        PackagingSchedule schedule = requireSchedule(sessionId);
        solutionManager.update(schedule, SolutionUpdatePolicy.UPDATE_ALL);
        repository.writeForSession(sessionId, schedule);
    }
}
