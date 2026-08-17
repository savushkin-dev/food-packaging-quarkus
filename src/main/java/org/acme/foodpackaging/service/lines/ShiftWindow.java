package org.acme.foodpackaging.service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ShiftWindow(LocalDateTime start, LocalDateTime end) {

    private static final int SHIFT_START_HOUR = 8;
    private static final int SHIFT_SPLIT_HOUR = 20;

    public static ShiftWindow forDate(LocalDate date, Integer smena) {
        if (smena == null) {
            return fullDay(date);
        }
        return switch (smena) {
            case 1 -> firstShift(date);
            case 2 -> secondShift(date);
            default -> throw new IllegalArgumentException("Unsupported smena value: " + smena);
        };
    }

    private static ShiftWindow fullDay(LocalDate date) {
        LocalDateTime start = date.atTime(SHIFT_START_HOUR, 0);
        return new ShiftWindow(start, start.plusDays(1));
    }

    private static ShiftWindow firstShift(LocalDate date) {
        LocalDateTime start = date.atTime(SHIFT_START_HOUR, 0);
        LocalDateTime end = date.atTime(SHIFT_SPLIT_HOUR, 0);
        return new ShiftWindow(start, end);
    }

    private static ShiftWindow secondShift(LocalDate date) {
        LocalDateTime start = date.atTime(SHIFT_SPLIT_HOUR, 0);
        LocalDateTime end = date.plusDays(1).atTime(SHIFT_START_HOUR, 0);
        return new ShiftWindow(start, end);
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

