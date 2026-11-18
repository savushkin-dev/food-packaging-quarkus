package org.acme.foodpackaging.service.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.BdVpmcRepository;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;

import java.time.LocalDateTime;
import java.util.function.BiConsumer;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProductionJobSaveService {

    private static final String CLEANING_NOTE = "Мойка, переналадка";

    private final BdVpmcRepository bdVpmcRepository;
    private final OeePevRepository oeePevRepository;

    public void save(Job job) {

        job.setCleaningFId(syncEvent(job, job.getCleaningFId(), hasCleaningOperation(job), this::fillCleaning));
        job.setDelayFId(syncEvent(job, job.getDelayFId(), job.getDelayDuration() != null, this::fillDelay));
        job.setCleaningFId(syncEvent(job, job.getCleaningFId(), job.getCleaningDelay() != null, this::fillCleaningDelay));

        updateProductionJob(job);
    }

    /**
     * Приводит запись OEE_PEV в соответствие с заданием: обновляет или создаёт её,
     * если событие есть, и удаляет, если события больше нет.
     *
     * @return F_ID записи, который должен остаться у задания
     */
    private Long syncEvent(Job job, Long fId, boolean present, BiConsumer<OeePev, Job> fill) {

        OeePev existing = fId != null ? oeePevRepository.findByFId(fId) : null;

        if (!present) {
            if (existing == null) {
                return fId;
            }
            oeePevRepository.delete(existing);
            return null;
        }

        if (existing != null) {
            fill.accept(existing, job);
            return fId;
        }

        OeePev entity = new OeePev();
        entity.setSnpz(job.getSnpz());
        fill.accept(entity, job);
        oeePevRepository.persist(entity);
        return entity.getFId();
    }

    private boolean hasCleaningOperation(Job job) {
        LocalDateTime startCleaning = job.getStartCleaningDateTime();
        LocalDateTime startProduction = job.getStartProductionDateTime();

        return startCleaning != null
                && startProduction != null
                && !startCleaning.isEqual(startProduction);
    }

    private void fillCleaning(OeePev entity, Job job) {
        LocalDateTime startCleaning = job.getStartCleaningDateTime();
        LocalDateTime startProduction = job.getStartProductionDateTime();

        entity.setLineId(job.getLine().getId());
        entity.setStartDateTime(startCleaning);
        entity.setEndDateTime(startProduction);
        entity.setDuration(DurationMinutes.between(startCleaning, startProduction));
        entity.setEventTypeId(null);
        entity.setReason(null);
        entity.setNote(CLEANING_NOTE);
    }

    private void fillDelay(OeePev entity, Job job) {
        LocalDateTime planEndDateTime = job.getPlanEndDateTime();
        // уже сохранённую запись без плановой даты окончания не трогаем
        if (planEndDateTime == null && entity.getFId() != null) return;

        entity.setLineId(job.getLine().getId());
        entity.setStartDateTime(planEndDateTime);
        entity.setEndDateTime(job.getEndDateTime());
        entity.setDuration(DurationMinutes.of(job.getDelayDuration()));
        entity.setEventTypeId(EventCode.PACKAGING_DELAY.getCode());
        entity.setReason(null);
        entity.setNote(job.getDelayNote());
    }

    private void fillCleaningDelay(OeePev entity, Job job) {
        LocalDateTime startProduction = job.getStartProductionDateTime();

        entity.setLineId(job.getLine().getId());
        entity.setStartDateTime(startProduction.minusMinutes(job.getCleaningDelay().toMinutes()));
        entity.setEndDateTime(startProduction);
        entity.setDuration(DurationMinutes.of(job.getCleaningDelay()));
        entity.setEventTypeId(EventCode.CLEANING_DELAY.getCode());
        entity.setReason(null);
        entity.setNote(job.getCleaningDelayNote());
    }

    private void updateProductionJob(Job job) {
        LocalDateTime startProduction = job.getStartProductionDateTime();
        LocalDateTime endDateTime = job.getEndDateTime();

        bdVpmcRepository.updateBySnpz(
                job.getSnpz(),
                startProduction,
                endDateTime,
                DurationMinutes.between(startProduction, endDateTime),
                job.getLine().getId(), job.isHandPackaging()
        );
    }
}
