package service.lines;

import fixtures.SolutionFixtures;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.LineProductionDto;
import org.acme.foodpackaging.repository.PmLogRepository;
import org.acme.foodpackaging.service.lines.LineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LineServiceTest {

    @InjectMocks
    LineService lineService;

    @Mock
    LoadDataService loadDataService;

    @Mock
    PmLogRepository pmLogRepository;

    @Test
    void getLines() {
        when(loadDataService.getLines())
                .thenReturn(new ConcurrentHashMap<>(Map.of("L1", "Line 1")));

        List<Line> result = lineService.getLines();

        assertEquals(1, result.size());
        assertEquals("L1", result.getFirst().getId());
        assertEquals("Line 1", result.getFirst().getName());

        verify(loadDataService).getLines();
    }

    @Test
    void getLines_shouldSortByExtractedLineNumber() {

        Map<String, String> linesMap = Map.of(
                "L3", "Линия № 3",
                "L1", "Линия № 1",
                "L2", "Линия № 2",
                "L10", "Линия № 10");
        when(loadDataService.getLines())
                .thenReturn(new ConcurrentHashMap<>(linesMap));

        List<Line> result = lineService.getLines();

        assertEquals(4, result.size());
        assertEquals("L1", result.get(0).getId());
        assertEquals("L2", result.get(1).getId());
        assertEquals("L3", result.get(2).getId());
        assertEquals("L10", result.get(3).getId());
    }

    @Test
    void getLines_shouldHandleLinesWithoutNumbers() {

        Map<String, String> linesMap = Map.of(
                "L3", "Линия № 3",
                "L_NO_NUM", "Линия без номера",
                "L1", "Линия № 1",
                "L_ANOTHER", "Другая линия");
        when(loadDataService.getLines())
                .thenReturn(new ConcurrentHashMap<>(linesMap));

        List<Line> result = lineService.getLines();

        assertEquals(4, result.size());
        assertTrue(result.get(0).getId().equals("L1") || result.get(1).getId().equals("L1"));
        assertTrue(result.get(0).getId().equals("L3") || result.get(1).getId().equals("L3"));
        assertTrue(result.get(2).getId().equals("L_NO_NUM") || result.get(3).getId().equals("L_NO_NUM"));
        assertTrue(result.get(2).getId().equals("L_ANOTHER") || result.get(3).getId().equals("L_ANOTHER"));
    }

    @Test
    void getLines_shouldHandleDifferentNumberFormats() {

        Map<String, String> linesMap = Map.of(
                "L1", "Линия №1",
                "L2", "Линия № 2",
                "L3", "Линия №  3");
        when(loadDataService.getLines())
                .thenReturn(new ConcurrentHashMap<>(linesMap));

        List<Line> result = lineService.getLines();

        assertEquals(3, result.size());
        assertEquals("L1", result.get(0).getId());
        assertEquals("L2", result.get(1).getId());
        assertEquals("L3", result.get(2).getId());
    }

    // ============================================================
    // initLineStartEnd
    // ============================================================
    @Test
    void initLineStartEnd_whenLineListIsNull() {
        assertDoesNotThrow(
                () -> lineService.initLineStartEnd(new PackagingSchedule()));
    }

    @Test
    void initLineStartEnd_success() {
        PackagingSchedule solution = SolutionFixtures.solutionWithLines();
        LocalDateTime defaultDateTime = solution.getWorkCalendar().getPlanningDate().atStartOfDay();
        lineService.initLineStartEnd(solution);

        Line lineWithJobs = solution.getLines().getLast();
        assertNull(solution.getLines().getFirst().getJobs());
        assertTrue(solution.getLines().get(1).getJobs().isEmpty());
        assertFalse(lineWithJobs.getJobs().isEmpty());

        assertEquals(defaultDateTime, solution.getLines().getFirst().getStartDateTime()); // when jobs is null
        assertEquals(defaultDateTime, solution.getLines().get(1).getStartDateTime()); // when jobs is empty

        assertEquals(lineWithJobs.getJobs().getFirst().getStartProductionDateTime(), lineWithJobs.getStartDateTime()); // line
                                                                                                                       // should
                                                                                                                       // start
                                                                                                                       // from
                                                                                                                       // first
                                                                                                                       // Job
        assertEquals(lineWithJobs.getJobs().getLast().getEndDateTime().plusHours(24), lineWithJobs.getMaxEndTime());
    }

    // ============================================================
    // setMaxEndDateTimeByLastJob
    // ============================================================
    @Test
    void setMaxEndDateTimeByLastJob_whenLineListIsNull() {
        assertDoesNotThrow(
                () -> lineService.setMaxEndDateTimeByLastJob(new PackagingSchedule()));
    }

    @Test
    void setMaxEndDateTimeByLastJob_success() {
        PackagingSchedule solution = SolutionFixtures.solutionWithLines();
        LocalDateTime defaultStartDateTime = solution.getWorkCalendar().getPlanningDate().atStartOfDay();
        LocalDateTime defaultEndDateTime = defaultStartDateTime.plusHours(24);
        LocalDateTime expectedLineStart = solution.getLines().getLast().getStartDateTime();
        LocalDateTime expectedLineEnd = solution.getLines().getLast().getJobs().getLast().getEndDateTime()
                .plusHours(24);

        lineService.setMaxEndDateTimeByLastJob(solution);

        Line lineWithJobs = solution.getLines().getLast();
        assertNull(solution.getLines().getFirst().getJobs());
        assertTrue(solution.getLines().get(1).getJobs().isEmpty());
        assertFalse(lineWithJobs.getJobs().isEmpty());

        assertEquals(defaultStartDateTime, solution.getLines().getFirst().getStartDateTime());
        assertEquals(defaultEndDateTime, solution.getLines().getFirst().getMaxEndTime()); // when jobs is null

        assertEquals(defaultStartDateTime, solution.getLines().get(1).getStartDateTime());
        assertEquals(defaultEndDateTime, solution.getLines().get(1).getMaxEndTime()); // when jobs is empty

        assertEquals(expectedLineStart, lineWithJobs.getStartDateTime()); // should not change
        assertEquals(expectedLineEnd, lineWithJobs.getMaxEndTime());
    }

    // ============================================================
    // calculateLineProductions
    // ============================================================
    @Test
    void calculateLineProductions_jobCrossesWindowStart_callsFromStartQuery() {
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(300.0);
        j1.setIdBatch("BATCH_START");
        j1.setCameraStart(date.atStartOfDay().plusHours(7).plusMinutes(50));
        j1.setCameraEnd(date.atStartOfDay().plusHours(8).plusMinutes(20));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        LocalDateTime windowStart = date.atTime(8, 0);

        when(pmLogRepository.getSuccessRateFromStart("BATCH_START", windowStart)).thenReturn(0.4);

        Map<String, LineProductionDto> result = lineService.calculateLineProductions(List.of(l1), date);

        assertEquals(120.0, result.get(String.valueOf(l1.getId())).massa()); // 300 * 0.4
        verify(pmLogRepository).getSuccessRateFromStart("BATCH_START", windowStart);
        verify(pmLogRepository, never()).getSuccessRateUntilEnd(any(), any());
    }

    @Test
    void calculateLineProductions_jobCrossesWindowEnd_callsUntilEndQuery() {
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(200.0);
        j1.setIdBatch("BATCH_END");
        j1.setCameraStart(date.plusDays(1).atStartOfDay().plusHours(7).plusMinutes(30));
        j1.setCameraEnd(date.plusDays(1).atStartOfDay().plusHours(8).plusMinutes(30));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        LocalDateTime windowEnd = date.atTime(8, 0).plusDays(1);

        when(pmLogRepository.getSuccessRateUntilEnd("BATCH_END", windowEnd)).thenReturn(0.75);

        Map<String, LineProductionDto> result = lineService.calculateLineProductions(List.of(l1), date);

        assertEquals(150.0, result.get(String.valueOf(l1.getId())).massa()); // 200 * 0.75
        verify(pmLogRepository).getSuccessRateUntilEnd("BATCH_END", windowEnd);
        verify(pmLogRepository, never()).getSuccessRateFromStart(any(), any());
    }

}