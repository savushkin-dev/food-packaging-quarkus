package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.BdVpmcRepository;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;

import java.time.Duration;
import java.time.LocalDateTime;

@ApplicationScoped
public class JobSaveService {

    private static final String CLEANING_NOTE = "Мойка, переналадка";
    private static final short DELETED_FLAG = 1;

    private final BdVpmcRepository bdVpmcRepository;
    private final OeePevRepository oeePevRepository;

    @Inject
    public JobSaveService(BdVpmcRepository bdVpmcRepository,
                          OeePevRepository oeePevRepository) {
        this.bdVpmcRepository = bdVpmcRepository;
        this.oeePevRepository = oeePevRepository;
    }

    @Transactional
    public void saveJobsByType(PackagingSchedule schedule) {

        markDeletedMaintenanceJobs(schedule);

        for (Job job : schedule.getJobs()) {

            if (job.isMaintenance()) {
                saveMaintenance(job);
            } else {
                saveProduction(job);
            }
        }
    }

    private void saveProduction(Job job) {

        syncCleaningOperation(job);
        updateProductionJob(job);
    }

    private void saveMaintenance(Job job) {

        if (job.isMaintenance()) {

            OeePev entity = buildMaintenanceOeePev(job);
            oeePevRepository.persist(entity);

            long fId = entity.getFId();
            job.setId(String.valueOf(fId));
            job.setFId(fId);

        } else {

            OeePev existing = oeePevRepository.findByFId(job.getFId());

            if (existing != null) {
                updateMaintenanceOeePev(existing, job);
                oeePevRepository.persist(existing);
            } else {

                OeePev entity = buildMaintenanceOeePev(job);
                oeePevRepository.persist(entity);

                long fId = entity.getFId();
                job.setId(String.valueOf(fId));
                job.setFId(fId);
            }
        }
    }

    private OeePev buildCleaningOeePev(Job job) {

        LocalDateTime startCleaning = job.getStartCleaningDateTime();
        LocalDateTime startProduction = job.getStartProductionDateTime();

        return OeePev.builder()
                .lineId(job.getLine().getId())
                .startProductionDateTime(startCleaning)
                .endDateTime(startProduction)
                .duration(calculateDurationMinutes(startCleaning, startProduction))
                .maintenanceTypeId(null)
                .reason(null)
                .note(CLEANING_NOTE)
                .snpz(job.getSnpz())
                .build();
    }

    private OeePev buildMaintenanceOeePev(Job job) {
        return OeePev.builder()
                .lineId(job.getLine().getId())
                .startProductionDateTime(job.getStartProductionDateTime())
                .endDateTime(job.getEndDateTime())
                .duration(calculateDurationMinutes(job.getStartProductionDateTime(), job.getEndDateTime()))
                .maintenanceTypeId(job.getMaintenanceTypeId())
                .reason(null)
                .note(job.getMaintenanceNote())
                .snpz(0L)
                .build();
    }

    private void updateProductionJob(Job job) {

        LocalDateTime startProduction = job.getStartProductionDateTime();
        LocalDateTime endDateTime = job.getEndDateTime();

        bdVpmcRepository.updateBySnpz(
                job.getSnpz(),
                startProduction,
                endDateTime,
                calculateDurationMinutes(startProduction, endDateTime),
                job.getLine().getId()
        );
    }

    private void updateCleaningOeePev(OeePev existing, Job job) {
        LocalDateTime startCleaning = job.getStartCleaningDateTime();
        LocalDateTime startProduction = job.getStartProductionDateTime();

        existing.setLineId(job.getLine().getId());
        existing.setStartProductionDateTime(startCleaning);
        existing.setEndDateTime(startProduction);
        existing.setDuration(calculateDurationMinutes(startCleaning, startProduction));
        existing.setMaintenanceTypeId(null);
        existing.setReason(null);
        existing.setNote(CLEANING_NOTE);
    }

    private void updateMaintenanceOeePev(OeePev existing, Job job) {

        LocalDateTime start = job.getStartProductionDateTime();
        LocalDateTime end = job.getEndDateTime();

        existing.setLineId(job.getLine().getId());
        existing.setStartProductionDateTime(start);
        existing.setEndDateTime(end);
        existing.setDuration(calculateDurationMinutes(start, end));
        existing.setMaintenanceTypeId(job.getMaintenanceTypeId());
        existing.setReason(null);
        existing.setNote(job.getMaintenanceNote());
        existing.setSnpz(0L);
    }

    private boolean shouldHaveCleaning(Job job) {

        if (job.isMaintenance()) {
            return false;
        }

        Job previous = job.getPreviousJob();

        if (previous == null) {
            return false;
        }

        if (previous.isMaintenance()) {
            return false;
        }

        LocalDateTime startCleaning = job.getStartCleaningDateTime();
        LocalDateTime startProduction = job.getStartProductionDateTime();

        return startCleaning != null
                && startProduction != null
                && startCleaning.isBefore(startProduction);
    }

    private void syncCleaningOperation(Job job) {

        OeePev existingCleaning = oeePevRepository.findBySnpz(job.getSnpz());

        if (shouldHaveCleaning(job)) {

            if (existingCleaning != null) {
                updateCleaningOeePev(existingCleaning, job);
                oeePevRepository.persist(existingCleaning);
            } else {
                OeePev entity = buildCleaningOeePev(job);
                oeePevRepository.persist(entity);
            }

        } else {

            if (existingCleaning != null) {
                oeePevRepository.delete(existingCleaning);
            }
        }
    }

    private void markDeletedMaintenanceJobs(PackagingSchedule schedule) {
        for (Job job : schedule.getJobs()) {
            if (job.isMaintenance() && job.getFDel() == DELETED_FLAG) {
                OeePev existing = oeePevRepository.findByFId(job.getFId());
                if (existing != null) {
                    existing.setFDel(DELETED_FLAG);
                    oeePevRepository.persist(existing);
                }
            }
        }
    }
    private int calculateDurationMinutes(LocalDateTime start, LocalDateTime end) {

        if (start == null || end == null) {
            return 0;
        }

        if (!end.isAfter(start)) {
            return 0;
        }

        return (int) Duration.between(start, end).toMinutes();
    }
}
