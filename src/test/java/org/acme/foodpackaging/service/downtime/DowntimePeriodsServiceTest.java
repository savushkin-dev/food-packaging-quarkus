package org.acme.foodpackaging.service.downtime;

import jakarta.ws.rs.WebApplicationException;
import org.acme.foodpackaging.dto.DowntimePeriodsResponse;
import org.acme.foodpackaging.repository.PmLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void build_emptyRows_throwsNotFound() {
        when(pmLogRepository.streamMarkingDtsByIdBatch("x")).thenReturn(Stream.empty());
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> service.build("x"));
        assertEquals(404, ex.getResponse().getStatus());
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
}
