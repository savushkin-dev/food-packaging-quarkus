package service.lines;

import fixtures.SolutionFixtures;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.load.LoadDataService;
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
                "L10", "Линия № 10"
        );
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
                "L_ANOTHER", "Другая линия"
        );
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
                "L3", "Линия №  3"
        );
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

        assertEquals(lineWithJobs.getJobs().getFirst().getStartProductionDateTime(), lineWithJobs.getStartDateTime());  // line should start from first Job
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
        LocalDateTime expectedLineEnd = solution.getLines().getLast().getJobs().getLast().getEndDateTime().plusHours(24);

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
    void calculateLineProductions() {
        Job j1 = new Job();
        Job j2 = new Job();
        Job j3 = new Job();
        Job j4 = new Job();
        Job j5 = new Job();
        Job j6 = new Job();

        j1.setMass(227.0);
        j2.setMass(227.0);

        LocalDate date = LocalDate.of(2026, Month.AUGUST, 3);
        j1.setCameraStart(date.atStartOfDay().plusHours(15));
        j1.setCameraEnd(j1.getCameraStart().plusHours(2));

        j2.setCameraStart(j1.getCameraEnd().plusHours(2));
        j2.setCameraEnd(j2.getCameraStart().plusHours(2));

        j3.setCameraStart(j2.getCameraEnd().plusDays(2));
        j3.setCameraEnd(j2.getCameraStart().plusHours(2));

        j5.setCameraStart(date.atStartOfDay().plusHours(9));
        j5.setCameraEnd(null);

        j6.setCameraStart(date.atStartOfDay().plusHours(10));
        j6.setCameraEnd(date.plusDays(1).atStartOfDay().plusHours(10));

        Line l1 = new Line("L1", "Line 1");
        Line l2 =  new Line("L2", "line2");
        Line l3 = new Line("L3", "line3");

        l1.setJobs(List.of(j1, j2, j3, j4, j5, j6));
        l2.setJobs(null);

        Map<String, Double> dailyProductions = lineService.calculateLineProductions(List.of(l1, l2, l3), date);

        double result = j1.getMass() + j2.getMass();
        assertEquals(result, dailyProductions.get(l1.getName()));
    }
}