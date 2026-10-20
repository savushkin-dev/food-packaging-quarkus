package org.acme.foodpackaging.service.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.entity.jobs.MsLog;
import org.acme.foodpackaging.repository.jobs.MsLogRepository;

import java.time.LocalDateTime;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DrawCleaningSaveService {

    private final MsLogRepository msLogRepository;

    public void save(Job job) {

        if (job.getDrawCleaningStart() == null
                && job.getDrawCleaningEnd() == null) {
            return;
        }

        int startCode = EventCode.DRAW_CLEANING_START.getCode();
        int endCode = EventCode.DRAW_CLEANING_END.getCode();

        MsLog drawCleaningStart = msLogRepository.findByIdBatchAndEvent(job.getIdBatch(), startCode);
        MsLog drawCleaningEnd = msLogRepository.findByIdBatchAndEvent(job.getIdBatch(), endCode);

        if (drawCleaningStart == null || drawCleaningEnd == null) {
            msLogRepository.persist(buildMsLog(job, startCode, job.getDrawCleaningStart()));
            msLogRepository.persist(buildMsLog(job, endCode, job.getDrawCleaningEnd()));
            return;
        }

        fill(drawCleaningStart, job, startCode, job.getDrawCleaningStart());
        fill(drawCleaningEnd, job, endCode, job.getDrawCleaningEnd());
    }

    private MsLog buildMsLog(Job job, Integer eventType, LocalDateTime eventTime) {
        MsLog entity = MsLog.builder()
                .idBatch(job.getIdBatch())
                .build();
        fill(entity, job, eventType, eventTime);
        return entity;
    }

    private void fill(MsLog entity, Job job, Integer eventType, LocalDateTime eventTime) {
        entity.setKmc(job.getProduct().getId());
        entity.setStartDateTimeFact(job.getDtv()); // DTV
        entity.setNp(job.getNp());
        entity.setEventType(eventType);
        entity.setEventTime(eventTime);           // DT
        entity.setLineIdFact(job.getLineIdFact());
    }
}
