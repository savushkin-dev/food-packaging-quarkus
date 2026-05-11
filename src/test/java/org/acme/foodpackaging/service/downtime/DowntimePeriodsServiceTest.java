package org.acme.foodpackaging.service.downtime;

import org.acme.foodpackaging.dto.DowntimePeriodsResponse;
import org.acme.foodpackaging.repository.PmLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DowntimePeriodsServiceTest {

    @Mock
    PmLogRepository pmLogRepository;

    DowntimePeriodsService service;

    @BeforeEach
    void setUp() {
        service = new DowntimePeriodsService(pmLogRepository);
    }

    @Test
    void build_emptyRows_returnsEmpty() {
        when(pmLogRepository.streamMarkingDtsByIdBatch("x")).thenReturn(Stream.empty());
        DowntimePeriodsResponse r = service.build("x");
        assertEquals("x", r.idBatch());
        assertNull(r.cameraStart());
        assertNull(r.cameraEnd());
        assertTrue(r.downtime().isEmpty());
    }

    @Test
    void build_singleRow_cameraEqualsAndNoDowntime() {
        LocalDateTime t = LocalDateTime.of(2026, 4, 27, 10, 0, 0);
        when(pmLogRepository.streamMarkingDtsByIdBatch("b")).thenReturn(Stream.of(t));

        DowntimePeriodsResponse r = service.build("b");

        assertEquals("b", r.idBatch());
        assertEquals(t, r.cameraStart());
        assertEquals(t, r.cameraEnd());
        assertTrue(r.downtime().isEmpty());
    }

    @Test
    void build_twoRows_exactlyTwoMinutes_noDowntime() {
        LocalDateTime a = LocalDateTime.of(2026, 4, 27, 10, 0, 0);
        LocalDateTime b = a.plusMinutes(2);
        when(pmLogRepository.streamMarkingDtsByIdBatch("b")).thenReturn(Stream.of(a, b));

        DowntimePeriodsResponse r = service.build("b");

        assertTrue(r.downtime().isEmpty());
    }

    @Test
    void build_twoRows_moreThanTwoMinutes_oneDowntime() {
        LocalDateTime a = LocalDateTime.of(2026, 4, 27, 10, 0, 0);
        LocalDateTime b = a.plusMinutes(2).plusSeconds(1);
        when(pmLogRepository.streamMarkingDtsByIdBatch("b")).thenReturn(Stream.of(a, b));

        DowntimePeriodsResponse r = service.build("b");

        assertEquals(1, r.downtime().size());
        assertEquals(a, r.downtime().getFirst().dtStart());
        assertEquals(b, r.downtime().getFirst().dtEnd());
    }

    @Test
    void build_reversedTimes_skipsPair() {
        LocalDateTime a = LocalDateTime.of(2026, 4, 27, 10, 0, 0);
        LocalDateTime b = a.minusMinutes(5);
        when(pmLogRepository.streamMarkingDtsByIdBatch("b")).thenReturn(Stream.of(a, b));

        DowntimePeriodsResponse r = service.build("b");

        assertTrue(r.downtime().isEmpty());
    }

    @Test
    void buildWithDuration_emptyRows_returnsEmpty() {
        when(pmLogRepository.streamMarkingDtsByIdBatch("x")).thenReturn(Stream.empty());
        DowntimePeriodsResponse r = service.build("x", Duration.ofMinutes(5));
        assertTrue(r.downtime().isEmpty());
    }

    @Test
    void buildWithDuration_filtersByProvidedMinutes() {
        LocalDateTime a = LocalDateTime.of(2026, 4, 27, 10, 0, 0);
        LocalDateTime b = a.plusMinutes(3);
        LocalDateTime c = b.plusMinutes(4);
        when(pmLogRepository.streamMarkingDtsByIdBatch("b")).thenReturn(Stream.of(a, b, c));

        DowntimePeriodsResponse r = service.build("b", Duration.ofMinutes(3));
        assertEquals(1, r.downtime().size());
        assertEquals(b, r.downtime().getFirst().dtStart());
        assertEquals(c, r.downtime().getFirst().dtEnd());
    }
}
