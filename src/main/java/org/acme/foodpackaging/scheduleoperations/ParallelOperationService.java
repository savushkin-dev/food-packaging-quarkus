package org.acme.foodpackaging.scheduleoperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.ParallelOperation;
import org.acme.foodpackaging.dto.request.paralleloperations.*;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ParallelOperationService {

    private final LoadDataService loadDataService;

    public PackagingSchedule add(PackagingSchedule schedule, AddParallelOperationRequest request) {

        String id = generateKey();
        String name = resolveName(request.eventTypeId());
        Duration duration = request.duration() != null
                ? Duration.ofMinutes(request.duration())
                : null;

        ParallelOperation operation = ParallelOperation.builder()
                .id(id)
                .lineId(request.lineId())
                .name(name)
                .startDateTime(request.startDateTime())
                .duration(duration)
                .endDateTime(calculateEndDateTime(request.startDateTime(), duration))
                .eventTypeId(request.eventTypeId())
                .note(request.note())
                .build();

        Map<String, ParallelOperation> operations = schedule.getParallelOperations();
        operations.put(id, operation);
        schedule.setParallelOperations(operations);

        return schedule;
    }

    public PackagingSchedule update(PackagingSchedule schedule, UpdateParallelOperationRequest request) {

        Map<String, ParallelOperation> operations = schedule.getParallelOperations();
        ParallelOperation existing = operations.get(request.id());
        if (existing == null) {
            throw new IllegalArgumentException("Parallel operation not found: " + request.id());
        }

        ParallelOperation.ParallelOperationBuilder builder = existing.toBuilder();

        if (request.lineId() != null) {
            builder.lineId(request.lineId());
        }
        if (request.eventTypeId() != null) {
            builder.eventTypeId(request.eventTypeId());
            builder.name(resolveName(request.eventTypeId()));
        }
        if (request.note() != null) {
            builder.note(request.note());
        }

        LocalDateTime newStart = request.startDateTime() != null ? request.startDateTime() : existing.getStartDateTime();
        Duration newDuration = request.duration() != null ? Duration.ofMinutes(request.duration()) : existing.getDuration();
        if (request.startDateTime() != null || request.duration() != null) {
            builder.startDateTime(newStart);
            builder.duration(newDuration);
            builder.endDateTime(calculateEndDateTime(newStart, newDuration));
        }

        ParallelOperation updated = builder.build();
        operations.put(updated.getId(), updated);
        schedule.setParallelOperations(operations);

        return schedule;
    }

    public PackagingSchedule remove(PackagingSchedule schedule, String id) {
        Map<String, ParallelOperation> operations = schedule.getParallelOperations();
        ParallelOperation removed = operations.remove(id);
        if (removed == null) {
            throw new IllegalArgumentException("Parallel operation not found: " + id);
        }
        schedule.setParallelOperations(operations);

        return schedule;
    }

    private String generateKey() {
        return UUID.randomUUID().toString();
    }

    private LocalDateTime calculateEndDateTime(LocalDateTime start, Duration duration) {
        return start == null || duration == null ? null : start.plus(duration);
    }

    private String resolveName(Integer eventTypeId) {
        if (eventTypeId == null || loadDataService == null) {
            return "Параллельная операция";
        }
        return loadDataService.getMaintenanceTypes().getOrDefault(eventTypeId, "Параллельная операция");
    }
}

