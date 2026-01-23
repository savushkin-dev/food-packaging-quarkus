package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.BdVpmcRepository;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JobSaveService {

    
    private static final String MAINTENANCE_PREFIX = "MAINTENANCE";
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
        
        List<Job> updatedJobs = new ArrayList<>(schedule.getJobs());
        
        for (int i = 0; i < schedule.getJobs().size(); i++) {
            Job job = schedule.getJobs().get(i);
            
            if (job.isMaintenance()) {
                Job updatedJob = saveMaintenanceJob(job);
                if (updatedJob != null) {
                    updatedJobs.set(i, updatedJob);
                }
            } else {
                saveRegularJob(job);
            }
        }
        
        schedule.setJobs(updatedJobs);
    }

    private void markDeletedMaintenanceJobs(PackagingSchedule schedule) {
        for (DbMaintenanceRow dbMaintenanceRow : schedule.getDbMaintenanceRowMap().values()) {
            if (dbMaintenanceRow.getFDel() == DELETED_FLAG) {
                OeePev existing = oeePevRepository.findByFId(dbMaintenanceRow.getFId());
                if (existing != null) {
                    existing.setFDel(DELETED_FLAG);
                    oeePevRepository.persist(existing);
                }
            }
        }
    }

    private Job saveMaintenanceJob(Job job) {
        if (isNewMaintenanceJob(job)) {
            return saveNewMaintenanceJob(job);
        } else {
            updateExistingMaintenanceJob(job);
            return null;
        }
    }

    private boolean isNewMaintenanceJob(Job job) {
        return job.getId() != null && job.getId().startsWith(MAINTENANCE_PREFIX);
    }

    private Job saveNewMaintenanceJob(Job job) {
        OeePev entity = buildMaintenanceOeePev(job);
        oeePevRepository.persist(entity);
        
        // After saving, get the assigned fId and assign it to the session plan
        long fId = entity.getFId();
        job.setId(String.valueOf(fId));
        job.setFId(fId);
        
        return job;
    }

    private void updateExistingMaintenanceJob(Job job) {
        OeePev existing = oeePevRepository.findByFId(job.getFId());
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
        existing.setMaintenanceTypeId(null);
        existing.setReason(null);
        existing.setNote(job.getName());
        existing.setSnpz(0L);
    }

    private void saveRegularJob(Job job) {
        if (hasCleaningOperation(job)) {
            saveCleaningOperation(job);
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

    private void saveCleaningOperation(Job job) {
        OeePev existing = oeePevRepository.findBySnpz(job.getSnpz());
        
        if (existing != null) {
            updateCleaningOeePev(existing, job);
            oeePevRepository.persist(existing);
        } else {
            OeePev entity = buildCleaningOeePev(job);
            oeePevRepository.persist(entity);
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

    private int calculateDurationMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return (int) Duration.between(start, end).toMinutes();
    }
}
