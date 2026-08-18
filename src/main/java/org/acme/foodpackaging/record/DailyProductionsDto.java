package org.acme.foodpackaging.record;

import java.time.LocalDate;

public record DailyProductionsDto(
        Integer shiftNumber,
        LocalDate selectedDate
) {
}
