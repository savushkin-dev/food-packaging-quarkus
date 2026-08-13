package service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;
import org.acme.foodpackaging.service.lines.ShiftWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import static io.smallrye.common.constraint.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiftWindowTest {

    private final LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);
    private final ShiftWindow window = ShiftWindow.forDate(date);

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

    @Test
    void fullyContains_jobStartAtOrAfterWindowEnd_false() {
        LocalDateTime start = date.plusDays(1).atTime(8, 0); // ровно на правой границе
        LocalDateTime end = start.plusHours(1);
        assertFalse(window.fullyContains(start, end)); // jobStart.isBefore(end) == false
    }

    @Test
    void fullyContains_jobEndBeforeWindowStart_false() {
        LocalDateTime start = date.atStartOfDay().plusHours(7);
        LocalDateTime end = date.atStartOfDay().plusHours(7).plusMinutes(30);
        assertFalse(window.fullyContains(start, end)); // !jobEnd.isBefore(start) == false
    }

    @Test
    void fullyContains_jobEndAtOrAfterWindowEnd_false() {
        LocalDateTime start = date.atStartOfDay().plusHours(20);
        LocalDateTime end = date.plusDays(1).atTime(8, 0);
        assertFalse(window.fullyContains(start, end)); // jobEnd.isBefore(end) == false
    }
}
