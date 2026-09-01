package service.lines;

import fixtures.SolutionFixtures;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.response.lineservice.BatchProductionDto;
import org.acme.foodpackaging.dto.response.lineservice.LineProductionDto;
import org.acme.foodpackaging.dto.response.lineservice.TotalProductionDto;
import org.acme.foodpackaging.persistence.load.LoadDataService;
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
    void calculateLineProductions_jobFullyInFirstShift_countedInShift1Only() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(227.0);
        j1.setNp(123);
        j1.setCameraStart(shiftStart.plusHours(7)); // 15:00
        j1.setCameraEnd(j1.getCameraStart().plusHours(2));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(227.0, dto.massa1());
        assertEquals(0.0, dto.massa2());
        assertEquals(227.0, dto.massa());
        assertEquals(1, dto.shift1().size());

        BatchProductionDto batch = dto.shift1().get(0);
        assertEquals("1", batch.snpz());
        assertEquals(227.0, batch.massa());
        assertEquals(123, batch.np());
        assertEquals(j1.getCameraStart(), batch.dts());
        assertEquals(j1.getCameraEnd(), batch.dte());

        assertTrue(dto.shift2().isEmpty());
    }

    @Test
    void calculateLineProductions_jobFullyInSecondShift_countedInShift2Only() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(150.0);
        j1.setCameraStart(shiftStart.plusHours(13)); // 21:00
        j1.setCameraEnd(j1.getCameraStart().plusHours(1));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.massa1());
        assertEquals(150.0, dto.massa2());
        assertEquals(150.0, dto.massa());
        assertTrue(dto.shift1().isEmpty());
        assertEquals(1, dto.shift2().size());
    }

    @Test
    void calculateLineProductions_jobsInBothShifts_totalIsSum() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job morningJob = new Job();
        morningJob.setId("1");
        morningJob.setMass(200.0);
        morningJob.setCameraStart(shiftStart.plusHours(2)); // 10:00
        morningJob.setCameraEnd(morningJob.getCameraStart().plusHours(1));

        Job eveningJob = new Job();
        eveningJob.setId("2");
        eveningJob.setMass(300.0);
        eveningJob.setCameraStart(shiftStart.plusHours(14)); // 22:00
        eveningJob.setCameraEnd(eveningJob.getCameraStart().plusHours(1));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(morningJob, eveningJob));

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(200.0, dto.massa1());
        assertEquals(300.0, dto.massa2());
        assertEquals(500.0, dto.massa());
    }

    @Test
    void calculateLineProductions_emptyJobs_zeroForBothShiftsAndTotal() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of());

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.massa1());
        assertEquals(0.0, dto.massa2());
        assertEquals(0.0, dto.massa());
        assertTrue(dto.shift1().isEmpty());
        assertTrue(dto.shift2().isEmpty());
    }

    @Test
    void calculateLineProductions_nullJobs_zeroForBothShiftsAndTotal() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(null);

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.massa());
        assertTrue(dto.shift1().isEmpty());
        assertTrue(dto.shift2().isEmpty());
    }

    @Test
    void calculateLineProductions_jobCrossesShift1ToShift2Boundary_usesUntilEndQuery() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(200.0);
        j1.setIdBatch("BATCH_BOUNDARY");
        j1.setCameraStart(shiftStart.plusHours(11).plusMinutes(30)); // 19:30
        j1.setCameraEnd(shiftStart.plusHours(12).plusMinutes(30)); // 20:30

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        LocalDateTime shift1End = shiftStart.plusHours(12); // 20:00
        when(pmLogRepository.getSuccessRateUntilEnd("BATCH_BOUNDARY", shift1End)).thenReturn(0.6);

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(120.0, dto.massa1()); // 200 * 0.6
        assertEquals(0.0, dto.massa2());
        assertEquals(120.0, dto.massa());
    }

    @Test
    void calculateLineProductions_jobCrossesWindowStart_usesFromStartQuery() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(300.0);
        j1.setIdBatch("BATCH_START");
        j1.setCameraStart(shiftStart.minusMinutes(10)); // 7:50
        j1.setCameraEnd(shiftStart.plusMinutes(20)); // 8:20

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        when(pmLogRepository.getSuccessRateFromStart("BATCH_START", shiftStart)).thenReturn(0.4);

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(120.0, dto.massa1()); // 300 * 0.4
        assertEquals(120.0, dto.massa());
    }

    @Test
    void calculateLineProductions_partialJobWithoutIdBatch_isSkipped() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(300.0);
        j1.setIdBatch(null);
        j1.setCameraStart(shiftStart.minusMinutes(10));
        j1.setCameraEnd(shiftStart.plusMinutes(20));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.massa());
        assertTrue(dto.shift1().isEmpty());
        verifyNoInteractions(pmLogRepository);
    }

    @Test
    void calculateLineProductions_successRateNull_treatedAsZero() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(300.0);
        j1.setIdBatch("BATCH_NO_DATA");
        j1.setCameraStart(shiftStart.minusMinutes(10));
        j1.setCameraEnd(shiftStart.plusMinutes(20));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        when(pmLogRepository.getSuccessRateFromStart("BATCH_NO_DATA", shiftStart)).thenReturn(null);

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.massa());
        assertTrue(dto.shift1().isEmpty());
    }

    @Test
    void calculateLineProductions_jobOutsideWindow_massIsZero() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(100.0);
        j1.setCameraStart(shiftStart.plusDays(5));
        j1.setCameraEnd(j1.getCameraStart().plusHours(1));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.massa());
        verifyNoInteractions(pmLogRepository);
    }

    @Test
    void calculateLineProductions_totalAggregatesAllLines() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(100.0);
        j1.setCameraStart(shiftStart.plusHours(2)); // 10:00
        j1.setCameraEnd(j1.getCameraStart().plusHours(1));

        Job j2 = new Job();
        j2.setId("2");
        j2.setMass(200.0);
        j2.setCameraStart(shiftStart.plusHours(14)); // 22:00
        j2.setCameraEnd(j2.getCameraStart().plusHours(1));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Line l2 = new Line("L2", "Line 2");
        l2.setJobs(List.of(j2));

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1, l2), shiftStart);

        TotalProductionDto total = (TotalProductionDto) result.get("total");
        assertEquals("Итого", total.name());
        assertEquals(100.0, total.massa1());
        assertEquals(200.0, total.massa2());
        assertEquals(300.0, total.massa());
    }

    @Test
    void calculateLineProductions_customShiftStartHour9_shiftsShiftedByOneHour() {
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(100.0);
        j1.setCameraStart(date.atStartOfDay().plusHours(8).plusMinutes(30));
        j1.setCameraEnd(j1.getCameraStart().plusHours(1));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Map<String, Object> resultDefault = lineService.calculateLineProductions(List.of(l1), date.atTime(8, 0));
        LineProductionDto dtoDefault = (LineProductionDto) resultDefault.get(String.valueOf(l1.getId()));
        assertEquals(100.0, dtoDefault.massa1());

        Map<String, Object> resultShifted = lineService.calculateLineProductions(List.of(l1), date.atTime(9, 0));
        LineProductionDto dtoShifted = (LineProductionDto) resultShifted.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dtoShifted.massa1());
    }

    @Test
    void calculateLineProductions_massIsRoundedToTwoDecimals() {
        LocalDateTime shiftStart = LocalDateTime.of(2026, Month.AUGUST, 3, 8, 0);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(300.0);
        j1.setIdBatch("BATCH_ROUND");
        j1.setCameraStart(shiftStart.minusMinutes(10));
        j1.setCameraEnd(shiftStart.plusMinutes(20));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        // 300 * 0.333333 = 99.9999 -> округляется до 100.0
        when(pmLogRepository.getSuccessRateFromStart("BATCH_ROUND", shiftStart)).thenReturn(0.333333);

        Map<String, Object> result = lineService.calculateLineProductions(List.of(l1), shiftStart);

        LineProductionDto dto = (LineProductionDto) result.get(String.valueOf(l1.getId()));
        assertEquals(100.0, dto.massa1());
        assertEquals(100.0, dto.shift1().get(0).massa());
    }
}
