package org.acme.foodpackaging.dto.oeepev;

public record DelayRow(
        Long fId,
        Long snpz,
        String note,
        Integer duration
) {
}
