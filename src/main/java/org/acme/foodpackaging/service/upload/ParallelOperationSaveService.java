package org.acme.foodpackaging.service.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.ParallelOperation;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ParallelOperationSaveService {

    private static final short DELETED_FLAG = 1;

    private final OeePevRepository oeePevRepository;

    public void markDeleted(PackagingSchedule schedule) {

        for (ParallelOperation operation : schedule.getDeletedParallelOperations()) {

            OeePev existing = findOeePev(operation.getId());

            if (existing != null) {
                existing.setFDel(DELETED_FLAG);
            }
        }
        schedule.setDeletedParallelOperations(null);
    }

    /**
     * Сохраняет параллельные операции в OEE_PEV. После сохранения id операции
     * (и ключ в карте) заменяется на сгенерированный F_ID
     */
    public void save(PackagingSchedule schedule) {

        Map<String, ParallelOperation> saved = new HashMap<>();

        for (ParallelOperation operation : schedule.getParallelOperations().values()) {

            OeePev existing = findOeePev(operation.getId());

            if (existing != null) {
                fill(existing, operation);
                saved.put(operation.getId(), operation);
                continue;
            }

            OeePev entity = new OeePev();
            fill(entity, operation);
            oeePevRepository.persist(entity);

            String id = String.valueOf(entity.getFId());
            saved.put(id, operation.toBuilder().id(id).build());
        }

        schedule.setParallelOperations(saved);
    }

    private OeePev findOeePev(String id) {
        try {
            OeePev existing = oeePevRepository.findByFId(Long.valueOf(id));
            return existing != null && Objects.equals(existing.getSnpz(), OeePev.PARALLEL_OPERATION_SNPZ)
                    ? existing : null;
        } catch (NumberFormatException e) {
            // id ещё не сохранённой операции — сгенерированный ключ, а не F_ID
            return null;
        }
    }

    private void fill(OeePev entity, ParallelOperation operation) {

        entity.setLineId(operation.getLineId());
        entity.setStartDateTime(operation.getStartDateTime());
        entity.setEndDateTime(operation.getEndDateTime());
        entity.setDuration(DurationMinutes.of(operation.getDuration()));
        entity.setEventTypeId(operation.getMaintenanceTypeId());
        entity.setReason(null);
        entity.setNote(operation.getMaintenanceNote());
        entity.setSnpz(OeePev.PARALLEL_OPERATION_SNPZ);
    }
}
