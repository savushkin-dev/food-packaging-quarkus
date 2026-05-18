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
                saveMaintenanceJob(job);
            } else {
                saveRegularJob(job);
                saveDelayDuration(job);
                saveCleaningDelayDuration(job);
            }
        }
    }

    private void markDeletedMaintenanceJobs(PackagingSchedule schedule) {
        for (Job job : schedule.getDeletedMaintenance()) {
            if (job.getFDel() == DELETED_FLAG) {
                OeePev existing = oeePevRepository.findByFId(job.getMaintenanceFId());
                if (existing != null) {
                    existing.setFDel(DELETED_FLAG);
                    oeePevRepository.persist(existing);
                }
            }
        }
    }

    private void saveMaintenanceJob(Job job) {
        if (job.isMaintenance() && job.getMaintenanceFId()==null) {
             saveNewMaintenanceJob(job);
        } else {
            updateExistingMaintenanceJob(job);
        }
    }

    private void  saveNewMaintenanceJob(Job job) {
        OeePev entity = buildMaintenanceOeePev(job);
        oeePevRepository.persist(entity);

        // After saving, get the assigned fId and assign it to the session plan
        long fId = entity.getFId();
        job.setId(String.valueOf(fId));
        job.setMaintenanceFId(fId);
    }

    private void updateExistingMaintenanceJob(Job job) {
        OeePev existing = oeePevRepository.findByFId(job.getMaintenanceFId());
        if (existing != null) {
            updateMaintenanceOeePev(existing, job);
            oeePevRepository.persist(existing);
        } else {
            // If not found, create new one
            OeePev entity = buildMaintenanceOeePev(job);
            oeePevRepository.persist(entity);
        }
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

    private void updateMaintenanceOeePev(OeePev existing, Job job) {
        existing.setLineId(job.getLine().getId());
        existing.setStartProductionDateTime(job.getStartProductionDateTime());
        existing.setEndDateTime(job.getEndDateTime());
        existing.setDuration(calculateDurationMinutes(job.getStartProductionDateTime(), job.getEndDateTime()));
        existing.setMaintenanceTypeId(job.getMaintenanceTypeId());
        existing.setReason(null);
        existing.setNote(job.getMaintenanceNote());
        existing.setSnpz(0L);
    }

    private void saveRegularJob(Job job) {

        OeePev existing = null;

        if (job.getCleaningFId() != null) {
            existing = oeePevRepository.findByFId(job.getCleaningFId());
        }

        if (hasCleaningOperation(job)) {

            if (existing != null) {
                updateCleaningOeePev(existing, job);

            } else {
                OeePev newEntity = buildCleaningOeePev(job);
                oeePevRepository.persist(newEntity);
                job.setCleaningFId(newEntity.getFId());
            }

        } else {

            if (existing != null) {
                oeePevRepository.delete(existing);
                job.setCleaningFId(null);
            }
        }
        updateProductionJob(job);
    }


    private void saveDelayDuration(Job job){

        OeePev existing = null;

        if (job.getDelayFId() != null) {
            existing = oeePevRepository.findByFId(job.getDelayFId());
        }

        if (hasDelayDuration(job)) {

            if (existing != null) {
                updateDelayOeePev(existing, job);

            } else {

                OeePev newEntity = buildDelayOeePev(job);
                oeePevRepository.persist(newEntity);
                job.setDelayFId(newEntity.getFId());
            }

        } else {

            if (existing != null) {
                oeePevRepository.delete(existing);
                job.setDelayFId(null);
            }
        }
        updateProductionJob(job);
    }

    private void saveCleaningDelayDuration(Job job){

        OeePev existing = null;

        if (job.getCleaningFId() != null) {
            existing = oeePevRepository.findByFId(job.getCleaningFId());
        }

        if (hasCleaningDelayDuration(job)) {

            if (existing != null) {
                updateCleaningDelayOeePev(existing, job);

            } else {

                OeePev newEntity = buildCleaningDelayOeePev(job);
                oeePevRepository.persist(newEntity);
                job.setCleaningFId(newEntity.getFId());
            }

        } else {

            if (existing != null) {
                oeePevRepository.delete(existing);
                job.setCleaningFId(null);
            }
        }

        updateProductionJob(job);
    }

    private boolean hasCleaningOperation(Job job) {
        LocalDateTime startCleaning = job.getStartCleaningDateTime();
        LocalDateTime startProduction = job.getStartProductionDateTime();

        return startCleaning != null
                && startProduction != null
                && !startCleaning.isEqual(startProduction);
    }

    private boolean hasDelayDuration(Job job) {
        return job.getDelayDuration() != null;
    }

    private boolean hasCleaningDelayDuration(Job job) {
        return job.getCleaningDelay() != null;
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

    private OeePev buildDelayOeePev(Job job) {
        LocalDateTime planEndDateTime = job.getPlanEndDateTime();
        LocalDateTime endDateTime = job.getEndDateTime();
        int delayMinutes = convertToIntDuration(job.getDelayDuration());

        return OeePev.builder()
                .lineId(job.getLine().getId())
                .startProductionDateTime(planEndDateTime)
                .endDateTime(endDateTime)
                .duration(delayMinutes)
                .maintenanceTypeId(10)
                .reason(null)
                .note(job.getDelayNote())
                .snpz(job.getSnpz())
                .build();
    }

    private OeePev buildCleaningDelayOeePev(Job job) {
        LocalDateTime planEndDateTime = job.getStartProductionDateTime().minusMinutes(job.getCleaningDelay().toMinutes());
        LocalDateTime endDateTime = job.getStartProductionDateTime();
        int delayMinutes = convertToIntDuration(job.getCleaningDelay());

        return OeePev.builder()
                .lineId(job.getLine().getId())
                .startProductionDateTime(planEndDateTime)
                .endDateTime(endDateTime)
                .duration(delayMinutes)
                .maintenanceTypeId(11)
                .reason(null)
                .note(job.getCleaningDelayNote())
                .snpz(job.getSnpz())
                .build();
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

    private void updateDelayOeePev(OeePev existing, Job job) {
        LocalDateTime planEndDateTime = job.getPlanEndDateTime();
        LocalDateTime  endDateTime = job.getEndDateTime();
        if(planEndDateTime == null ) return;

        int delayMinutes = convertToIntDuration(job.getDelayDuration());
        existing.setLineId(job.getLine().getId());
        existing.setStartProductionDateTime(planEndDateTime);
        existing.setEndDateTime(endDateTime);
        existing.setDuration(delayMinutes);
        existing.setMaintenanceTypeId(10);
        existing.setReason(null);
        existing.setNote(job.getDelayNote());
    }

    private void updateCleaningDelayOeePev(OeePev existing, Job job) {
        LocalDateTime planEndDateTime = job.getStartProductionDateTime().minusMinutes(job.getCleaningDelay().toMinutes());
        LocalDateTime  endDateTime = job.getStartProductionDateTime();

        int delayMinutes = convertToIntDuration(job.getCleaningDelay());
        existing.setLineId(job.getLine().getId());
        existing.setStartProductionDateTime(planEndDateTime);
        existing.setEndDateTime(endDateTime);
        existing.setDuration(delayMinutes);
        existing.setMaintenanceTypeId(11);
        existing.setReason(null);
        existing.setNote(job.getCleaningDelayNote());
    }

    private void updateProductionJob(Job job) {
        LocalDateTime startProduction = job.getStartProductionDateTime();
        LocalDateTime endDateTime = job.getEndDateTime();

        bdVpmcRepository.updateBySnpz(
                job.getSnpz(),
                startProduction,
                endDateTime,
                calculateDurationMinutes(startProduction, endDateTime),
                job.getLine().getId(), job.isHandPackaging()
        );
    }

    private int calculateDurationMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return (int) Duration.between(start, end).toMinutes();
    }

    private Integer convertToIntDuration(Duration duration){
        if(duration == null) return  null;
        long durationMinutes = duration.toMinutes();
        return (int) durationMinutes;
    }
}
