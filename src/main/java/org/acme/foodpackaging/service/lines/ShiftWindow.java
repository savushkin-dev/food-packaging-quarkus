package org.acme.foodpackaging.service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ShiftWindow(LocalDateTime start, LocalDateTime end) {

    public static ShiftWindow forDate(LocalDate date) {
        LocalDateTime start = date.atTime(8, 0);
        return new ShiftWindow(start, start.plusDays(1));
    }

    public boolean overlaps(LocalDateTime jobStart, LocalDateTime jobEnd) {
        LocalDateTime truncatedStart = truncateToMinutes(jobStart);
        LocalDateTime truncatedEnd = truncateToMinutes(jobEnd);
        return truncatedStart.isBefore(end) && truncatedEnd.isAfter(start);
    }

    public boolean fullyContains(LocalDateTime jobStart, LocalDateTime jobEnd) {
        LocalDateTime truncatedStart = truncateToMinutes(jobStart);
        LocalDateTime truncatedEnd = truncateToMinutes(jobEnd);
        return !truncatedStart.isBefore(start) && truncatedStart.isBefore(end)
                && !truncatedEnd.isBefore(start) && truncatedEnd.isBefore(end);
    }

    public WindowCrossing crossingType(LocalDateTime jobStart) {
        return truncateToMinutes(jobStart).isBefore(start) ? WindowCrossing.CROSSES_START : WindowCrossing.CROSSES_END;
    }

    private static LocalDateTime truncateToMinutes(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.MINUTES);
    }
}

