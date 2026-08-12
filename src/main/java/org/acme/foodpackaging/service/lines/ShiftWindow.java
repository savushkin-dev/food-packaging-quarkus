package org.acme.foodpackaging.service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShiftWindow(LocalDateTime start, LocalDateTime end) {

    public static ShiftWindow forDate(LocalDate date) {
        LocalDateTime start = date.atTime(8, 0);
        return new ShiftWindow(start, start.plusDays(1));
    }

    public boolean overlaps(LocalDateTime jobStart, LocalDateTime jobEnd) {
        return jobStart.isBefore(end) && jobEnd.isAfter(start);
    }

    public boolean fullyContains(LocalDateTime jobStart, LocalDateTime jobEnd) {
        return !jobStart.isBefore(start) && jobStart.isBefore(end)
                && !jobEnd.isBefore(start) && jobEnd.isBefore(end);
    }

    public WindowCrossing crossingType(LocalDateTime jobStart, LocalDateTime jobEnd) {
        if (jobStart.isBefore(start)) {
            return WindowCrossing.CROSSES_START; // начался до окна, закончился внутри
        }
        return WindowCrossing.CROSSES_END; // начался внутри, закончился после окна
    }
}
