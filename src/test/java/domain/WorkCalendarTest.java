package domain;

import org.acme.foodpackaging.domain.WorkCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkCalendarTest {

    @Test
    void noArgConstructor() {
        WorkCalendar calendar = new WorkCalendar();

        assertNull(calendar.getFromDate());
        assertNull(calendar.getToDate());
        assertNull(calendar.getMinStartDateTime());
    }

    @Test
    void constructorWithTwoDates() {
        LocalDate fromDate = LocalDate.of(2025, 1, 15);
        LocalDate toDate = LocalDate.of(2025, 1, 20);

        WorkCalendar calendar = new WorkCalendar(fromDate, toDate);

        assertEquals(fromDate, calendar.getFromDate());
        assertEquals(toDate, calendar.getToDate());
        assertNull(calendar.getMinStartDateTime());
    }

    @Test
    void constructorWithSingleDate() {
        LocalDate startDate = LocalDate.of(2025, 1, 15);

        WorkCalendar calendar = new WorkCalendar(startDate);

        assertEquals(startDate.minusDays(1), calendar.getFromDate());
        assertEquals(startDate.plusDays(3), calendar.getToDate());
        assertEquals(startDate.atStartOfDay(), calendar.getMinStartDateTime());
    }

    @Test
    void settersAndGetters() {
        WorkCalendar calendar = new WorkCalendar();
        LocalDate fromDate = LocalDate.of(2025, 1, 15);
        LocalDate toDate = LocalDate.of(2025, 1, 20);
        LocalDateTime minStart = LocalDateTime.of(2025, 1, 15, 8, 0);
        LocalDateTime idealEnd = LocalDateTime.of(2025, 1, 20, 17, 0);
        LocalDateTime maxEnd = LocalDateTime.of(2025, 1, 20, 20, 0);

        calendar.setFromDate(fromDate);
        calendar.setToDate(toDate);
        calendar.setMinStartDateTime(minStart);
        calendar.setIdealEndDateTime(idealEnd);
        calendar.setMaxEndDateTime(maxEnd);

        assertEquals(fromDate, calendar.getFromDate());
        assertEquals(toDate, calendar.getToDate());
        assertEquals(minStart, calendar.getMinStartDateTime());
        assertEquals(idealEnd, calendar.getIdealEndDateTime());
        assertEquals(maxEnd, calendar.getMaxEndDateTime());
    }
}