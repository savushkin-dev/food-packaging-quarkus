package org.acme.foodpackaging.service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ShiftWindow(LocalDateTime start, LocalDateTime end) {

    private static final int SHIFT_DURATION_HOURS = 12;

    public static ShiftWindow forShiftStart(LocalDateTime shiftStart, Integer shiftNumber) {
        if (shiftNumber == null) {
            return new ShiftWindow(shiftStart, shiftStart.plusHours(24));
        }
        return switch (shiftNumber) {
            case 1 -> new ShiftWindow(shiftStart, shiftStart.plusHours(SHIFT_DURATION_HOURS));
            case 2 -> new ShiftWindow(shiftStart.plusHours(SHIFT_DURATION_HOURS), shiftStart.plusHours(24));
            default -> throw new IllegalArgumentException("Unsupported shiftNumber value: " + shiftNumber);
        };
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