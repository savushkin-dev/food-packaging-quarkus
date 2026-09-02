package org.acme.foodpackaging.dto.plrlc;

import java.time.LocalDate;

public record EquipmentPeriodDto(
        String lineId,
        LocalDate begin,
        LocalDate end
) {
}

