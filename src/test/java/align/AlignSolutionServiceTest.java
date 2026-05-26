package align;

import builder.JobTestBuilder;
import builder.LineTestBuilder;
import builder.ScheduleTestBuilder;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.service.align.AlignCleaningService;
import org.acme.foodpackaging.service.align.AlignDurationService;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.lines.LineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlignSolutionServiceTest {

    @Mock
    LineService lineService;
    @Mock
    AlignDurationService alignDuration;
    @Mock
    AlignCleaningService cleaningService;

    private AlignSolutionService alignSolution;
    private PackagingSchedule solution;

    @BeforeEach
    void setUp() {
        alignSolution = new AlignSolutionService(
                alignDuration,
                cleaningService,
                lineService
        );
        Job mj1 = JobTestBuilder.aJob().withId("Mj1").asMaintenance().withMaintenanceTypeId(7).build();
        Job mj2 = JobTestBuilder.aJob().withId("Mj2").asMaintenance().withMaintenanceTypeId(2).build();
        Job mj3 = JobTestBuilder.aJob().withId("Mj3").asMaintenance().withMaintenanceTypeId(8).build();
        Job mj4 = JobTestBuilder.aJob().withId("Mj4").asMaintenance().withMaintenanceTypeId(2).build();

        Line line = LineTestBuilder.aLine("L1").withJobs(mj1, mj2, mj3, mj4).build();
        solution = ScheduleTestBuilder.aSchedule().withLines(line).withJobs(line.getJobs()).build();

    }

    @Test
    void shouldCallAllServicesInOrder() {
        PackagingSchedule schedule = new PackagingSchedule();
        alignSolution.align(schedule);

        verify(alignDuration).alignByFactDuration(schedule);
        verify(cleaningService).alignCleanings(schedule);
        verify(lineService).setMaxEndDateTimeByLastJob(schedule);
    }

    // ============================================================
    // removeAlignMaintenance
    // ============================================================

    @Test
    void removeAlignMaintenance_success() {
        alignSolution.align(solution);
        assertEquals(1, solution.getJobs().size());
        assertEquals(1, solution.getLines().getFirst().getJobs().size());
        assertEquals(3, solution.getDeletedMaintenance().size());

        assertEquals(2, solution.getJobs().getFirst().getMaintenanceTypeId());
        assertEquals("Mj2", solution.getJobs().getFirst().getId());
    }

    @Test
    void removeAlignMaintenance_whenLinesListIsNull() {
        solution.setLines(null);
        alignSolution.align(solution);
        assertDoesNotThrow(() -> alignSolution.align((solution)));
    }

    @Test
    void removeAlignMaintenance_whenLinesListIsEmpty() {
        solution.setLines(new ArrayList<>());
        alignSolution.align(solution);
        assertDoesNotThrow(() -> alignSolution.align((solution)));
    }

    @Test
    void removeAlignMaintenance_whenLinesListHasNull() {
        solution.getLines().add(null);
        alignSolution.align(solution);
        assertDoesNotThrow(() -> alignSolution.align((solution)));
    }

    @Test
    void removeAlignMaintenance_whenLineJobListIsNull() {
        solution.getLines().getFirst().setJobs(null);
        alignSolution.align(solution);

        assertEquals(1, solution.getJobs().size());
        assertEquals(3, solution.getDeletedMaintenance().size());
        assertEquals(2, solution.getJobs().getFirst().getMaintenanceTypeId());
        assertEquals("Mj2", solution.getJobs().getFirst().getId());
        assertNull(solution.getLines().getFirst().getJobs());
    }

    @Test
    void removeAlignMaintenance_whenLineJobListIsEmpty() {
        solution.getLines().getFirst().setJobs(new ArrayList<>());
        alignSolution.align(solution);

        assertEquals(1, solution.getJobs().size());
        assertEquals(3, solution.getDeletedMaintenance().size());
        assertEquals(2, solution.getJobs().getFirst().getMaintenanceTypeId());
        assertEquals("Mj2", solution.getJobs().getFirst().getId());
        assertTrue(solution.getLines().getFirst().getJobs().isEmpty());
    }

    @Test
    void removeAlignMaintenance_whenSolutionJobListIsNull() {
        assertDoesNotThrow(() -> alignSolution.align((new PackagingSchedule())));
    }

    @Test
    void removeAlignMaintenance_whenSolutionJobListIsEmpty() {
        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());
        assertDoesNotThrow(() -> alignSolution.align((schedule)));
    }

    // ============================================================
    // reset
    // ============================================================
    @Test
    void resetAlign_success() {
        Job j1 = new Job();
        j1.setDelayDuration(Duration.ZERO);
        j1.setCleaningDelay(Duration.ZERO);
        alignSolution.reset(solution);

        assertNull(solution.getJobs().getFirst().getDelayDuration());
        assertNull(solution.getJobs().getFirst().getCleaningDelay());
    }

    @Test
    void resetAlign_whenSolutionIsNull() {
       solution.setJobs(null);
        assertDoesNotThrow(() -> alignSolution.reset((solution)));
    }

    @Test
    void resetAlign_whenSolutionJobsListIsNull() {
        assertDoesNotThrow(() -> alignSolution.reset((null)));
    }

    // ============================================================
    // alignFromScratch
    // ============================================================

    @Test
    void shouldCallResetBeforeAlign() {
        PackagingSchedule schedule = new PackagingSchedule();

        AlignSolutionService spyAlignSolution = spy(alignSolution);

        doNothing().when(spyAlignSolution).reset(any());
        doNothing().when(spyAlignSolution).align(any());

        spyAlignSolution.alignFromScratch(schedule);

        InOrder inOrder = inOrder(spyAlignSolution);

        inOrder.verify(spyAlignSolution).reset(schedule);
        inOrder.verify(spyAlignSolution).align(schedule);
    }
}