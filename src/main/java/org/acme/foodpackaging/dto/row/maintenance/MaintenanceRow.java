package org.acme.foodpackaging.dto.row.maintenance;

import org.acme.foodpackaging.entity.jobs.OeePev;

import java.time.LocalDateTime;

public record MaintenanceRow(
        Long fId,
        String lineId,
        String note,
        LocalDateTime startProductionDateTime,
        Integer duration,
        Integer eventTypeId,
        Long snpz
) {
    public boolean isParallelOperation() {
        return snpz != null && snpz == OeePev.PARALLEL_OPERATION_SNPZ;
    }
}
