package service.lines;

import fixtures.SolutionFixtures;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.dto.LineProductionDto;
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
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(227.0);
        j1.setCameraStart(date.atStartOfDay().plusHours(15));
        j1.setCameraEnd(j1.getCameraStart().plusHours(2));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Map<String, LineProductionDto> result = lineService.calculateLineProductions(List.of(l1), date);

        LineProductionDto dto = result.get(String.valueOf(l1.getId()));
        assertEquals(227.0, dto.shift1().massa());
        assertEquals(Map.of("1", 227.0), dto.shift1().snpz());
        assertEquals(0.0, dto.shift2().massa());
        assertTrue(dto.shift2().snpz().isEmpty());
        assertEquals(227.0, dto.totalMassa());
    }

    @Test
    void calculateLineProductions_jobFullyInSecondShift_countedInShift2Only() {
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(150.0);
        j1.setCameraStart(date.atStartOfDay().plusHours(21));
        j1.setCameraEnd(j1.getCameraStart().plusHours(1));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        Map<String, LineProductionDto> result = lineService.calculateLineProductions(List.of(l1), date);

        LineProductionDto dto = result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.shift1().massa());
        assertEquals(150.0, dto.shift2().massa());
        assertEquals(150.0, dto.totalMassa());
    }

    @Test
    void calculateLineProductions_jobsInBothShifts_totalIsSum() {
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Job morningJob = new Job();
        morningJob.setId("1");
        morningJob.setMass(200.0);
        morningJob.setCameraStart(date.atStartOfDay().plusHours(10));
        morningJob.setCameraEnd(morningJob.getCameraStart().plusHours(1));

        Job eveningJob = new Job();
        eveningJob.setId("2");
        eveningJob.setMass(300.0);
        eveningJob.setCameraStart(date.atStartOfDay().plusHours(22));
        eveningJob.setCameraEnd(eveningJob.getCameraStart().plusHours(1));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(morningJob, eveningJob));

        Map<String, LineProductionDto> result = lineService.calculateLineProductions(List.of(l1), date);

        LineProductionDto dto = result.get(String.valueOf(l1.getId()));
        assertEquals(200.0, dto.shift1().massa());
        assertEquals(300.0, dto.shift2().massa());
        assertEquals(500.0, dto.totalMassa());
    }

    @Test
    void calculateLineProductions_emptyJobs_zeroForBothShiftsAndTotal() {
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of());

        Map<String, LineProductionDto> result = lineService.calculateLineProductions(List.of(l1), date);

        LineProductionDto dto = result.get(String.valueOf(l1.getId()));
        assertEquals(0.0, dto.shift1().massa());
        assertEquals(0.0, dto.shift2().massa());
        assertEquals(0.0, dto.totalMassa());
    }

    @Test
    void calculateLineProductions_jobCrossesShift1ToShift2Boundary_usesUntilEndQuery() {
        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);

        Job j1 = new Job();
        j1.setId("1");
        j1.setMass(200.0);
        j1.setIdBatch("BATCH_BOUNDARY");
        j1.setCameraStart(date.atStartOfDay().plusHours(19).plusMinutes(30));
        j1.setCameraEnd(date.atStartOfDay().plusHours(20).plusMinutes(30));

        Line l1 = new Line("L1", "Line 1");
        l1.setJobs(List.of(j1));

        LocalDateTime shift1End = date.atTime(20, 0);
        when(pmLogRepository.getSuccessRateUntilEnd("BATCH_BOUNDARY", shift1End)).thenReturn(0.6);

        Map<String, LineProductionDto> result = lineService.calculateLineProductions(List.of(l1), date);

        LineProductionDto dto = result.get(String.valueOf(l1.getId()));
        assertEquals(120.0, dto.shift1().massa()); // 200 * 0.6, попадает в конец 1-й смены
        assertEquals(0.0, dto.shift2().massa()); // остаток job'а после границы не считается отдельно — это оставшаяся

        assertEquals(120.0, dto.totalMassa());
    }
}