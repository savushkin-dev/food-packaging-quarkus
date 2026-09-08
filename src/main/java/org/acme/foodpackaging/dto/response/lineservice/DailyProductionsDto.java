package org.acme.foodpackaging.dto.response.lineservice;

import java.time.LocalDate;

public record DailyProductionsDto(
        Integer shiftNumber,
        LocalDate selectedDate
) {
}
