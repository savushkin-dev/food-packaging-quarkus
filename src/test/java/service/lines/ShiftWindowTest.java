package service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;
import org.acme.foodpackaging.service.lines.ShiftWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;

import static io.smallrye.common.constraint.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.*;

class ShiftWindowTest {

    private final LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);
    private final ShiftWindow window = ShiftWindow.forShiftStart(shiftStart, null);

    // --- crossingType ---

    @Test
    void crossingType_jobStartsBeforeWindow_crossesStart() {
        LocalDateTime start = shiftStart.minusHours(0).minusMinutes(10); // 7:50
        assertEquals(WindowCrossing.CROSSES_START, window.crossingType(start));
    }

    @Test
    void crossingType_jobStartsInsideWindow_crossesEnd() {
        LocalDateTime start = shiftStart.plusHours(23).minusMinutes(30); // next day 7:30
        assertEquals(WindowCrossing.CROSSES_END, window.crossingType(start));
    }

    // --- overlaps ---

    @Test
    void overlaps_jobStartsAfterWindowEnd_false() {
        // jobStart.isBefore(end) == false -> короткое замыкание, jobEnd не оценивается
        LocalDateTime start = shiftStart.plusDays(2);
        LocalDateTime end = start.plusHours(1);
        assertFalse(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobEndsBeforeWindowStart_false() {
        // jobStart.isBefore(end) == true, доходим до jobEnd.isAfter(start), там false
        LocalDateTime start = shiftStart.minusHours(3);
        LocalDateTime end = shiftStart.minusHours(2);
        assertFalse(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobInsideWindow_true() {
        // оба условия true
        LocalDateTime start = shiftStart.plusHours(7);
        LocalDateTime end = start.plusHours(2);
        assertTrue(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobEndsJustAfterWindowStartBySeconds_ignoredAsSameMinute() {
        // shiftStart + 23s после округления до минут == window.start, пересечения нет
        LocalDateTime start = shiftStart.minusMinutes(31);
        LocalDateTime end = shiftStart.plusSeconds(23);
        assertFalse(window.overlaps(start, end));
    }

    @Test
    void overlaps_jobEndsFullMinuteAfterWindowStart_trueOverlap() {
        // shiftStart + 1 минута после округления остаётся после window.start
        LocalDateTime start = shiftStart.minusMinutes(10);
        LocalDateTime end = shiftStart.plusMinutes(1);
        assertTrue(window.overlaps(start, end));
    }

    // --- fullyContains ---

    @Test
    void fullyContains_jobInsideWindow_true() {
        // все 4 условия true
        LocalDateTime start = shiftStart.plusHours(7);
        LocalDateTime end = start.plusHours(2);
        assertTrue(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobStartsBeforeWindow_false() {
        // !jobStart.isBefore(start) == false -> первое условие
        LocalDateTime start = shiftStart.minusMinutes(10);
        LocalDateTime end = shiftStart.plusMinutes(20);
        assertFalse(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobStartAtOrAfterWindowEnd_false() {
        // jobStart.isBefore(end) == false -> второе условие
        LocalDateTime start = shiftStart.plusHours(24);
        LocalDateTime end = start.plusHours(1);
        assertFalse(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobEndBeforeWindowStart_false() {
        // jobStart внутри окна (первые 2 условия true), а jobEnd раньше window.start
        // -> !jobEnd.isBefore(start) == false, третье условие
        LocalDateTime start = shiftStart.plusHours(1);
        LocalDateTime end = shiftStart.minusMinutes(30);
        assertFalse(window.fullyContains(start, end));
    }

    @Test
    void fullyContains_jobEndAtOrAfterWindowEnd_false() {
        // первые три условия true, jobEnd.isBefore(end) == false -> четвёртое условие
        LocalDateTime start = shiftStart.plusHours(12);
        LocalDateTime end = shiftStart.plusHours(24);
        assertFalse(window.fullyContains(start, end));
    }

    // --- forShiftStart ---

    @Test
    void forShiftStart_shiftNumberNull_fullDayWindow() {
        ShiftWindow noShiftWindow = ShiftWindow.forShiftStart(shiftStart, null);
        assertEquals(shiftStart, noShiftWindow.start());
        assertEquals(shiftStart.plusHours(24), noShiftWindow.end());
    }

    @Test
    void forShiftStart_shiftNumber1_firstShiftWindow() {
        ShiftWindow firstWindow = ShiftWindow.forShiftStart(shiftStart, 1);
        assertEquals(shiftStart, firstWindow.start());
        assertEquals(shiftStart.plusHours(12), firstWindow.end());
    }

    @Test
    void forShiftStart_shiftNumber2_secondShiftWindow() {
        ShiftWindow secondWindow = ShiftWindow.forShiftStart(shiftStart, 2);
        assertEquals(shiftStart.plusHours(12), secondWindow.start());
        assertEquals(shiftStart.plusHours(24), secondWindow.end());
    }

    @Test
    void forShiftStart_unsupportedShiftNumber_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> ShiftWindow.forShiftStart(shiftStart, 3));
    }

    @Test
    void forShiftStart_customShiftStartHour9_windowsShiftedByOneHour() {
        LocalDateTime shiftStart9 = LocalDateTime.of(2026, Month.AUGUST, 3, 9, 0);

        ShiftWindow firstWindow = ShiftWindow.forShiftStart(shiftStart9, 1);
        assertEquals(shiftStart9, firstWindow.start());
        assertEquals(shiftStart9.plusHours(12), firstWindow.end());

        ShiftWindow secondWindow = ShiftWindow.forShiftStart(shiftStart9, 2);
        assertEquals(shiftStart9.plusHours(12), secondWindow.start());
        assertEquals(shiftStart9.plusHours(24), secondWindow.end());
    }
}