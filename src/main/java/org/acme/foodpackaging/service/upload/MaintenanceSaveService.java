package org.acme.foodpackaging.service.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MaintenanceSaveService {

    private static final short DELETED_FLAG = 1;

    private final OeePevRepository oeePevRepository;

    public void markDeleted(PackagingSchedule schedule) {

        for (Job job : schedule.getDeletedMaintenance()) {

            if (job.getFDel() != DELETED_FLAG) {
                continue;
            }

            OeePev existing = findOeePev(job.getId());

            if (existing != null) {
                existing.setFDel(DELETED_FLAG);
            }
        }
    }

    public void save(Job job) {

        OeePev existing = findOeePev(job.getId());

        if (existing != null) {
            fill(existing, job);
            return;
        }

        OeePev entity = new OeePev();
        fill(entity, job);

        oeePevRepository.persist(entity);
        job.setId(String.valueOf(entity.getFId()));
    }

    private OeePev findOeePev(String id) {

        if (id == null || id.isBlank()) {
            return null;
        }

        try {
            return oeePevRepository.findByFId(Long.valueOf(id));
        } catch (NumberFormatException ignored) {
            // Ignore invalid ids
            return null;
        }
    }

    private void fill(OeePev entity, Job job) {

        entity.setLineId(job.getLine().getId());
        entity.setStartDateTime(job.getStartProductionDateTime());
        entity.setEndDateTime(job.getEndDateTime());

        entity.setDuration(DurationMinutes.between(
                job.getStartProductionDateTime(),
                job.getEndDateTime()
        ));

        entity.setEventTypeId(job.getMaintenanceTypeId());
        entity.setReason(null);
        entity.setNote(job.getMaintenanceNote());
        entity.setSnpz(0L);
    }
}
