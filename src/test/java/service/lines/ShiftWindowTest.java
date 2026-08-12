package service.lines;

import org.acme.foodpackaging.persistence.constants.WindowCrossing;
import org.acme.foodpackaging.service.lines.ShiftWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiftWindowTest {

    private final LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);
    private final ShiftWindow window = ShiftWindow.forDate(date);

    @Test
    void crossingType_jobStartsBeforeWindow_crossesStart() {
        LocalDateTime start = date.atStartOfDay().plusHours(7).plusMinutes(50);
        LocalDateTime end = date.atStartOfDay().plusHours(8).plusMinutes(20);
        assertEquals(WindowCrossing.CROSSES_START, window.crossingType(start, end));
    }

    @Test
    void crossingType_jobEndsAfterWindow_crossesEnd() {
        LocalDateTime start = date.plusDays(1).atStartOfDay().plusHours(7).plusMinutes(30);
        LocalDateTime end = date.plusDays(1).atStartOfDay().plusHours(8).plusMinutes(30);
        assertEquals(WindowCrossing.CROSSES_END, window.crossingType(start, end));
    }
}
