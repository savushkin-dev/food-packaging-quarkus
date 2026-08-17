package service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;
import org.acme.foodpackaging.service.lines.ShiftWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import static io.smallrye.common.constraint.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.*;

class ShiftWindowTest {

    private final LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);
    private final ShiftWindow window = ShiftWindow.forDate(date, null);

    // --- crossingType ---

    @Test
    void crossingType_jobStartsBeforeWindow_crossesStart() {
        LocalDateTime start = date.atStartOfDay().plusHours(7).plusMinutes(50);
        assertEquals(WindowCrossing.CROSSES_START, window.crossingType(start));
    }

    @Test
    void crossingType_jobStartsInsideWindow_crossesEnd() {
        LocalDateTime start = date.plusDays(1).atStartOfDay().plusHours(7).plusMinutes(30);
        assertEquals(WindowCrossing.CROSSES_END, window.crossingType(start));
    }

    // --- overlaps ---

    @Test
    void overlaps_jobStartsAfterWindowEnd_false() {
        // jobStart.isBefore(end) == false -> короткое замыкание, jobEnd не оценивается
        LocalDateTime start = date.plusDays(2).atStartOfDay();
        LocalDateTime end = start.plusHours(1);
        assertFalse(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobEndsBeforeWindowStart_false() {
        // jobStart.isBefore(end) == true, доходим до jobEnd.isAfter(start), там false
        LocalDateTime start = date.atStartOfDay().plusHours(5);
        LocalDateTime end = date.atStartOfDay().plusHours(6);
        assertFalse(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobInsideWindow_true() {
        // оба условия true
        LocalDateTime start = date.atStartOfDay().plusHours(15);
        LocalDateTime end = start.plusHours(2);
        assertTrue(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobEndsJustAfterWindowStartBySeconds_ignoredAsSameMinute() {
        // 8:00:23 после округления до минут == window.start (8:00:00), пересечения нет
        LocalDateTime start = date.atStartOfDay().plusHours(7).plusMinutes(29);
        LocalDateTime end = date.atTime(8, 0, 23);
        assertFalse(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobEndsFullMinuteAfterWindowStart_trueOverlap() {
        // 8:01:00 после округления остаётся 8:01:00, это уже после window.start
        LocalDateTime start = date.atStartOfDay().plusHours(7).plusMinutes(50);
        LocalDateTime end = date.atTime(8, 1, 0);
        assertTrue(window.overlaps(start, end));
    }

    // --- fullyContains ---

    @Test
    void fullyContains_jobInsideWindow_true() {
        // все 4 условия true
        LocalDateTime start = date.atStartOfDay().plusHours(15);
        LocalDateTime end = start.plusHours(2);
        assertTrue(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobStartsBeforeWindow_false() {
        // !jobStart.isBefore(start) == false -> первое условие
        LocalDateTime start = date.atStartOfDay().plusHours(7).plusMinutes(50);
        LocalDateTime end = date.atStartOfDay().plusHours(8).plusMinutes(20);
        assertFalse(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobStartAtOrAfterWindowEnd_false() {
        // jobStart.isBefore(end) == false -> второе условие
        LocalDateTime start = date.plusDays(1).atTime(8, 0);
        LocalDateTime end = start.plusHours(1);
        assertFalse(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobEndBeforeWindowStart_false() {
        // jobStart внутри окна (первые 2 условия true), а jobEnd раньше window.start
        // -> !jobEnd.isBefore(start) == false, третье условие
        LocalDateTime start = date.atStartOfDay().plusHours(9);
        LocalDateTime end = date.atStartOfDay().plusHours(7).plusMinutes(30);
        assertFalse(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobEndAtOrAfterWindowEnd_false() {
        // первые три условия true, jobEnd.isBefore(end) == false -> четвёртое условие
        LocalDateTime start = date.atStartOfDay().plusHours(20);
        LocalDateTime end = date.plusDays(1).atTime(8, 0);
        assertFalse(window.fullyContains(start, end));
    }

    @Test
    void forDate_shiftNumberNull_fullDayWindow() {
        ShiftWindow window = ShiftWindow.forDate(date, null);
        assertEquals(date.atTime(8, 0), window.start());
        assertEquals(date.plusDays(1).atTime(8, 0), window.end());
    }

    @Test
    void forDate_shiftNumber1_firstShiftWindow() {
        ShiftWindow window = ShiftWindow.forDate(date, 1);
        assertEquals(date.atTime(8, 0), window.start());
        assertEquals(date.atTime(20, 0), window.end());
    }

    @Test
    void forDate_shiftNumber2_secondShiftWindow() {
        ShiftWindow window = ShiftWindow.forDate(date, 2);
        assertEquals(date.atTime(20, 0), window.start());
        assertEquals(date.plusDays(1).atTime(8, 0), window.end());
    }

    @Test
    void forDate_unsupportedshiftNumber_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> ShiftWindow.forDate(date, 3));
    }
}