package org.acme.foodpackaging.dto.row.maintenance;

import java.time.LocalDateTime;

public record MaintenanceRow(
        Long fId,
        String lineId,
        String note,
        LocalDateTime startProductionDateTime,
        Integer duration,
        Integer eventTypeId
) {
}
