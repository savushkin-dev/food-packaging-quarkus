package org.acme.foodpackaging.dto.row.jobs;

import org.acme.foodpackaging.domain.Job;

import java.time.LocalDateTime;

public record MsLogInsertRow(
        String idBatch,
        String productId,
        String lineIdFact,
        Integer np,
        Integer eventType,
        LocalDateTime dtv,
        LocalDateTime eventTime
) {
    public MsLogInsertRow(Job job, int eventType, LocalDateTime eventTime) {
        this(job.getIdBatch(), job.getProduct().getId(), job.getLineIdFact(),
                job.getNp(), eventType, job.getDtv(), eventTime);
    }
}
