package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.jobs.MsLog;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.BdVpmcRepository;
import org.acme.foodpackaging.repository.jobs.MsLogRepository;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;
import org.acme.foodpackaging.persistence.constants.EventCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobSaveService {

    private static final String CLEANING_NOTE = "Мойка, переналадка";
    private static final short DELETED_FLAG = 1;

    private final BdVpmcRepository bdVpmcRepository;
    private final OeePevRepository oeePevRepository;
    private final MsLogRepository msLogRepository;

    @Transactional
    public void saveJobsByType(PackagingSchedule schedule) {

        markDeletedMaintenanceJobs(schedule);

        for (Job job : schedule.getJobs()) {

            if (job.isMaintenance()) {
                saveMaintenanceJob(job);
                continue;
            }

            saveRegularJob(job);
            saveDelayDuration(job);
            saveCleaningDelayDuration(job);
            saveDrawCleaningEvents(job);
        }
    }

    private void markDeletedMaintenanceJobs(PackagingSchedule schedule) {

        for (Job job : schedule.getDeletedMaintenance()) {

            if (job.getFDel() != DELETED_FLAG) {
                continue;
            }

            try {
                Long id = Long.valueOf(job.getId());

                OeePev existing = oeePevRepository.findByFId(id);

                if (existing != null) {
                    existing.setFDel(DELETED_FLAG);
                }

            } catch (NumberFormatException ignored) {
                // Ignore invalid ids
            }
        }
    }

    private void saveMaintenanceJob(Job job) {

        if (job.getId() == null || job.getId().isBlank()) {
            saveNewMaintenanceJob(job);
            return;
        }

        try {
            Long id = Long.valueOf(job.getId());

            OeePev existing = oeePevRepository.findByFId(id);

            if (existing == null) {
                saveNewMaintenanceJob(job);
                return;
            }

            updateMaintenanceOeePev(existing, job);

        } catch (NumberFormatException e) {
            saveNewMaintenanceJob(job);
        }
    }

    private void saveNewMaintenanceJob(Job job) {

        OeePev entity = buildMaintenanceOeePev(job);

        oeePevRepository.persist(entity);
        job.setId(String.valueOf(entity.getFId()));
    }

    private OeePev buildMaintenanceOeePev(Job job) {

        return OeePev.builder()
                .lineId(job.getLine().getId())
                .startDateTime(job.getStartProductionDateTime())
                .endDateTime(job.getEndDateTime())
                .duration(calculateDurationMinutes(
                        job.getStartProductionDateTime(),
                        job.getEndDateTime()
                ))
                .eventTypeId(job.getMaintenanceTypeId())
                .reason(null)
                .note(job.getMaintenanceNote())
                .snpz(0L)
                .build();
    }

    private void updateMaintenanceOeePev(OeePev existing, Job job) {

        existing.setLineId(job.getLine().getId());
        existing.setStartDateTime(job.getStartProductionDateTime());
        existing.setEndDateTime(job.getEndDateTime());

        existing.setDuration(calculateDurationMinutes(
                job.getStartProductionDateTime(),
                job.getEndDateTime()
        ));

        existing.setEventTypeId(job.getMaintenanceTypeId());
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


    private void saveDelayDuration(Job job) {

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

    private void saveCleaningDelayDuration(Job job) {

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
                .startDateTime(startCleaning)
                .endDateTime(startProduction)
                .duration(calculateDurationMinutes(startCleaning, startProduction))
                .eventTypeId(null)
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
                .startDateTime(planEndDateTime)
                .endDateTime(endDateTime)
                .duration(delayMinutes)
                .eventTypeId(EventCode.PACKAGING_DELAY.getCode())
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
                .startDateTime(planEndDateTime)
                .endDateTime(endDateTime)
                .duration(delayMinutes)
                .eventTypeId(EventCode.CLEANING_DELAY.getCode())
                .reason(null)
                .note(job.getCleaningDelayNote())
                .snpz(job.getSnpz())
                .build();
    }

    private void updateCleaningOeePev(OeePev existing, Job job) {
        LocalDateTime startCleaning = job.getStartCleaningDateTime();
        LocalDateTime startProduction = job.getStartProductionDateTime();

        existing.setLineId(job.getLine().getId());
        existing.setStartDateTime(startCleaning);
        existing.setEndDateTime(startProduction);
        existing.setDuration(calculateDurationMinutes(startCleaning, startProduction));
        existing.setEventTypeId(null);
        existing.setReason(null);
        existing.setNote(CLEANING_NOTE);
    }

    private void updateDelayOeePev(OeePev existing, Job job) {
        LocalDateTime planEndDateTime = job.getPlanEndDateTime();
        LocalDateTime endDateTime = job.getEndDateTime();
        if (planEndDateTime == null) return;

        int delayMinutes = convertToIntDuration(job.getDelayDuration());
        existing.setLineId(job.getLine().getId());
        existing.setStartDateTime(planEndDateTime);
        existing.setEndDateTime(endDateTime);
        existing.setDuration(delayMinutes);
        existing.setEventTypeId(EventCode.PACKAGING_DELAY.getCode());
        existing.setReason(null);
        existing.setNote(job.getDelayNote());
    }

    private void updateCleaningDelayOeePev(OeePev existing, Job job) {
        LocalDateTime planEndDateTime = job.getStartProductionDateTime().minusMinutes(job.getCleaningDelay().toMinutes());
        LocalDateTime endDateTime = job.getStartProductionDateTime();

        int delayMinutes = convertToIntDuration(job.getCleaningDelay());
        existing.setLineId(job.getLine().getId());
        existing.setStartDateTime(planEndDateTime);
        existing.setEndDateTime(endDateTime);
        existing.setDuration(delayMinutes);
        existing.setEventTypeId(EventCode.CLEANING_DELAY.getCode());
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

    // ============================================================
    // DrawCleaningEvents
    // ========================================================
    private void saveDrawCleaningEvents(Job job) {

        if (job.getDrawCleaningStart() == null
                && job.getDrawCleaningEnd() == null) {
            return;
        }

        saveCleaningInfo(job);
    }

    private void saveCleaningInfo(Job job) {

        MsLog existing = msLogRepository.findByIdBatchAndEvent(
                job.getIdBatch(),
                EventCode.DRAW_CLEANING.getCode()
        );

        if (existing == null) {
            saveNewCleaningInfo(job);
            return;
        }

        updateCleaningInfo(existing, job);
    }

    private void saveNewCleaningInfo(Job job) {

        MsLog entity = buildDrawCleaningMsLog(job);

        msLogRepository.persist(entity);
    }

    private MsLog buildDrawCleaningMsLog(Job job) {

        return MsLog.builder()
                .idBatch(job.getIdBatch())
                .kmc(job.getProduct().getId())
                .startDateTimeFact(job.getDrawCleaningStart()) // DTV
                .np(job.getNp())
                .eventType(EventCode.DRAW_CLEANING.getCode())
                .eventTime(job.getDrawCleaningEnd())           // DT
                .lineIdFact(job.getLineIdFact())
                .build();
    }

    private void updateCleaningInfo(MsLog existing, Job job) {
        existing.setKmc(job.getProduct().getId());
        existing.setStartDateTimeFact(job.getDrawCleaningStart()); // DTV
        existing.setNp(job.getNp());
        existing.setEventType(EventCode.DRAW_CLEANING.getCode());
        existing.setEventTime(job.getDrawCleaningEnd());           // DT
        existing.setLineIdFact(job.getLineIdFact());
    }

    // ============================================================
    // Help methods
    // ========================================================
    private int calculateDurationMinutes(LocalDateTime start, LocalDateTime end) {
        ZoneId zoneId = ZoneId.systemDefault();
        if (start == null || end == null) {
            return 0;
        }
        return (int) Duration.between(start.atZone(zoneId), end.atZone(zoneId)).toMinutes();
    }

    private Integer convertToIntDuration(Duration duration) {
        if (duration == null) return null;
        long durationMinutes = duration.toMinutes();
        return (int) durationMinutes;
    }
}
