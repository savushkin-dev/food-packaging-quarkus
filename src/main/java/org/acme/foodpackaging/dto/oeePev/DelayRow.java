package org.acme.foodpackaging.dto.oeePev;

public record DelayRow(
        Long fId,
        Long snpz,
        String note,
        Integer duration
) {
}
