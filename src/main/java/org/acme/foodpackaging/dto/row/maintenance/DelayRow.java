package org.acme.foodpackaging.dto.row.maintenance;

public record DelayRow(
        Long fId,
        Long snpz,
        String note,
        Integer duration
) {
}
