package org.acme.foodpackaging.scheduleoperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.ParallelOperation;
import org.acme.foodpackaging.dto.request.paralleloperations.*;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ParallelOperationService {

    private final LoadDataService loadDataService;

    /**
     * Добавляет параллельную сервисную операцию, генерируя уникальный ключ
     */
    public PackagingSchedule add(PackagingSchedule schedule, AddParallelOperationRequest request) {

        String id = generateKey();
        String name = resolveName(request.eventTypeId());

        ParallelOperation operation = ParallelOperation.builder()
                .id(id)
                .lineId(request.lineId())
                .name(name)
                .startDateTime(request.startDateTime())
                .duration(request.duration())
                .endDateTime(calculateEndDateTime(request.startDateTime(), request.duration()))
                .eventTypeId(request.eventTypeId())
                .note(request.note())
                .build();

        schedule.getParallelOperations().put(id, operation);
        return schedule;
    }

    /**
     * Обновляет существующую параллельную операцию по id — устанавливаются только
     * переданные поля
     */
    public PackagingSchedule update(PackagingSchedule schedule, UpdateParallelOperationRequest request) {

        ParallelOperation existing = findById(schedule, request.id());
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

        LocalDateTime newStart = request.startDateTime() != null ? request.startDateTime()
                : existing.getStartDateTime();
        var newDuration = request.duration() != null ? request.duration() : existing.getDuration();
        if (request.startDateTime() != null || request.duration() != null) {
            builder.startDateTime(newStart);
            builder.duration(newDuration);
            builder.endDateTime(calculateEndDateTime(newStart, newDuration));
        }

        ParallelOperation updated = builder.build();
        schedule.getParallelOperations().put(updated.getId(), updated);

        return schedule;
    }

    /**
     * Удаляет параллельную операцию по ключу
     */
    public PackagingSchedule remove(PackagingSchedule schedule, String id) {
        ParallelOperation removed = schedule.getParallelOperations().remove(id);
        if (removed == null) {
            throw new IllegalArgumentException("Parallel operation not found: " + id);
        }
        return schedule;
    }

    private ParallelOperation findById(PackagingSchedule schedule, String id) {
        ParallelOperation operation = schedule.getParallelOperations().get(id);
        if (operation == null) {
            throw new IllegalArgumentException("Parallel operation not found: " + id);
        }
        return operation;
    }

    private String generateKey() {
        return UUID.randomUUID().toString();
    }

    private LocalDateTime calculateEndDateTime(LocalDateTime start, java.time.Duration duration) {
        return start == null || duration == null ? null : start.plus(duration);
    }

    private String resolveName(Integer eventTypeId) {
        if (eventTypeId == null || loadDataService == null) {
            return "Параллельная операция";
        }
        return loadDataService.getMaintenanceTypes().getOrDefault(eventTypeId, "Параллельная операция");
    }
}
