package org.acme.foodpackaging.dto.row.lines;

import java.time.LocalDate;

public record EquipmentPeriodRow(
        String lineId,
        LocalDate begin,
        LocalDate end
) {
}

