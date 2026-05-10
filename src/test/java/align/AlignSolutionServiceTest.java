package align;

import builder.ScheduleTestBuilder;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.service.align.AlignCleaningService;
import org.acme.foodpackaging.service.align.AlignDurationService;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.lines.LineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlignSolutionServiceTest {

    @Mock
    MaintenanceJob maintenanceJob;
    @Mock
    LineService lineService;
    @Mock
    AlignDurationService alignDuration;

    @Mock
    AlignCleaningService cleaningService;

    private AlignSolutionService alignSolution;
    private PackagingSchedule solution;
    private Line line;
    private Job j1;

    @BeforeEach
    void setUp() {
        alignSolution = new AlignSolutionService(
                alignDuration,
                cleaningService,
                lineService
        );
        solution = new PackagingSchedule();
        line = new Line();
        j1 = new Job();
        j1.setId("j1");
        solution.setJobs(new ArrayList<>(List.of(j1)));
        line.setJobs(new ArrayList<>(List.of(j1)));
        solution.setLines(new ArrayList<>(List.of(line)));

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
    void removeAlignMaintenance_maintenanceTypeId() {
        j1.setMaintenanceTypeId(8);
        j1.setMaintenance(true);
        alignSolution.align(solution);
        assertTrue(solution.getJobs().isEmpty());
        assertTrue(solution.getLines().getFirst().getJobs().isEmpty());
        assertEquals(1, solution.getDeletedMaintenance().size());
        assertEquals(1, solution.getDeletedMaintenance().getFirst().getFDel());
        assertEquals("j1", solution.getDeletedMaintenance().getFirst().getId());
    }

    @Test
    void removeAlignMaintenance_previousAlign() {
        Job j2 = new Job();
        solution.getJobs().add(j2);
        solution.getLines().getFirst().getJobs().add(j2);
        j2.setMaintenance(true);
        j2.setMaintenanceTypeId(2);
        j2.setPreviousJob(j1);

        j1.setMaintenanceTypeId(8);
        j1.setMaintenance(true);
        alignSolution.align(solution);
        assertTrue(solution.getJobs().isEmpty());
        assertTrue(solution.getLines().getFirst().getJobs().isEmpty());
        assertEquals(2, solution.getDeletedMaintenance().size());
    }
    
    // ============================================================
    // reset
    // ============================================================
    @Test
    void resetAlign_success() {
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
}