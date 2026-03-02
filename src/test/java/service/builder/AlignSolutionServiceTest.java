package service.builder;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.service.builder.AlignSolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlignSolutionServiceTest {

    @InjectMocks
    AlignSolutionService alignSolutionService;

    @Mock
    MaintenanceJob maintenanceJob;

    private PackagingSchedule schedule;
    private Line line;

    @BeforeEach
    void setUp() {
        schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());

        line = new Line("line1", "Line 1");
        line.setJobs(new ArrayList<>());

        schedule.setLines(List.of(line));
    }

    // ============================================================
    // alignByFactDuration
    // ============================================================

    @Test
    void alignByFactDuration_whenFactGreaterThanPlan_shouldAddMaintenance() {

        Job job = createPlanJob(
                LocalDateTime.of(2025,1,15,10,0),
                90);

        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        ArgumentCaptor<MaintenanceRequest> captor =
                ArgumentCaptor.forClass(MaintenanceRequest.class);

        verify(maintenanceJob).addMaintenanceJob(eq(schedule), captor.capture());

        MaintenanceRequest request = captor.getValue();
        assertEquals(30, request.getDurationMinutes());
        assertEquals(7, request.getMaintenanceTypeId());
    }

    @Test
    void alignByFactDuration_whenFactLessOrEqualPlan_shouldNotAddMaintenance() {

        Job job = createPlanJob(
                LocalDateTime.of(2025,1,15,10,0),
                60);

        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenJobHasNullCameraTimes_shouldSkipJob() {
        Job job = new Job();
        job.setId("J1");
        job.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        job.setEndDateTime(LocalDateTime.of(2025, 1, 15, 11, 0));
        job.setCameraStart(null);
        job.setCameraEnd(null);

        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenNextJobIsDeviationOrAlignMaintenance_shouldNotAddMaintenance() {
        Job job = createPlanJob(LocalDateTime.of(2025, 1, 15, 10, 0), 90);
        Job maintenanceNext = new Job();
        maintenanceNext.setMaintenance(true);
        maintenanceNext.setMaintenanceTypeId(7);
        job.setNextJob(maintenanceNext);

        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenNextJobIsAlignMaintenanceType8_shouldNotAddMaintenance() {
        Job job = createPlanJob(LocalDateTime.of(2025, 1, 15, 10, 0), 90);
        Job maintenanceNext = new Job();
        maintenanceNext.setMaintenance(true);
        maintenanceNext.setMaintenanceTypeId(8);
        job.setNextJob(maintenanceNext);

        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenLineHasNullJobs_shouldSkipLine() {
        line.setJobs(null);
        schedule.setLines(List.of(line));

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenLineHasEmptyJobs_shouldSkipLine() {
        line.getJobs().clear();

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenJobHasNullPlanDates_shouldUseZeroPlanMinutesAndAddMaintenance() {
        Job job = new Job();
        job.setId("J1");
        job.setStartProductionDateTime(null);
        job.setEndDateTime(null);
        job.setCameraStart(LocalDateTime.of(2025, 1, 15, 10, 0));
        job.setCameraEnd(LocalDateTime.of(2025, 1, 15, 11, 0));

        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        ArgumentCaptor<MaintenanceRequest> captor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), captor.capture());
        assertEquals(60, captor.getValue().getDurationMinutes());
    }

    // ============================================================
    // alignLineStartByFact
    // ============================================================

    @Test
    void alignLineStartByFact_whenFactEqualsPlan_shouldNotAddMaintenance() {

        Product product = createProduct("P1");

        Job j1 = createChainJob("J1", product, 1,
                LocalDateTime.of(2025,1,15,10,0),
                0);

        Job j2 = createChainJob("J2", product, 2,
                LocalDateTime.of(2025,1,15,11,0),
                0);

        line.getJobs().addAll(List.of(j1, j2));
        schedule.getJobs().addAll(List.of(j1, j2));

        alignSolutionService.alignLineStartByFact(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenAlignMaintenanceDurationAlreadyMatches_shouldNotUpdate() {
        Product product = createProduct("P1");
        Job align = new Job();
        align.setMaintenance(true);
        align.setMaintenanceTypeId(8);
        align.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        align.setDuration(Duration.ofMinutes(30));

        Job j1 = createChainJob("J1", product, 1, LocalDateTime.of(2025, 1, 15, 9, 30), 0);
        j1.setPreviousJob(align);
        Job j2 = createChainJob("J2", product, 2, LocalDateTime.of(2025, 1, 15, 10, 30), 0);
        line.getJobs().addAll(List.of(align, j1, j2));
        schedule.getJobs().addAll(List.of(align, j1, j2));

        alignSolutionService.alignLineStartByFact(schedule);

        verify(maintenanceJob, never()).updateDuration(any(), any());
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenAlignMaintenanceDurationDiffers_shouldUpdate() {

        Product product = createProduct("P1");

        Job align = new Job();
        align.setMaintenance(true);
        align.setMaintenanceTypeId(8);
        align.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        align.setDuration(Duration.ofMinutes(10)); // старая длительность

        Job j1 = createChainJob("J1", product, 1,
                LocalDateTime.of(2025, 1, 15, 9, 30), 0);

        j1.setPreviousJob(align);

        Job j2 = createChainJob("J2", product, 2,
                LocalDateTime.of(2025, 1, 15, 10, 30), 0);

        line.getJobs().addAll(List.of(align, j1, j2));
        schedule.getJobs().addAll(List.of(align, j1, j2));

        alignSolutionService.alignLineStartByFact(schedule);

        verify(maintenanceJob).updateDuration(eq(schedule), any());
    }

    @Test
    void alignLineStartByFact_whenPlanBeforeFact_shouldAddAlignMaintenance() {

        Product product = createProduct("P1");

        Job j1 = createChainJob("J1", product, 1,
                LocalDateTime.of(2025, 1, 15, 9, 0), 30); // факт сдвинут на 30 мин

        Job j2 = createChainJob("J2", product, 2,
                LocalDateTime.of(2025, 1, 15, 10, 0), 30);

        line.getJobs().addAll(List.of(j1, j2));
        schedule.getJobs().addAll(List.of(j1, j2));

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> captor =
                ArgumentCaptor.forClass(MaintenanceRequest.class);

        verify(maintenanceJob).addMaintenanceJob(eq(schedule), captor.capture());

        MaintenanceRequest request = captor.getValue();
        assertEquals(8, request.getMaintenanceTypeId());
        assertTrue(request.getDurationMinutes() > 0);
    }
    // ============================================================
    // helpers
    // ============================================================

    private Product createProduct(String id) {
        Product p = new Product();
        p.setId(id); // ⚠ id обязательно для цепочек
        return p;
    }

    private Job createPlanJob(LocalDateTime planStart,
                              long factMinutes) {

        Job job = new Job();
        job.setId("J1");
        job.setStartProductionDateTime(planStart);
        job.setEndDateTime(planStart.plusMinutes(60));

        job.setCameraStart(planStart);
        job.setCameraEnd(planStart.plusMinutes(factMinutes));

        return job;
    }

    private Job createChainJob(String id,
                               Product product,
                               int np,
                               LocalDateTime planStart,
                               long factShiftMinutes) {

        Job job = new Job();
        job.setId(id);
        job.setProduct(product);
        job.setNp(np);

        job.setStartCleaningDateTime(planStart);   // ВАЖНО
        job.setStartProductionDateTime(planStart);

        job.setEndDateTime(planStart.plusMinutes(60));

        job.setCameraStart(planStart.plusMinutes(factShiftMinutes));
        job.setCameraEnd(planStart.plusMinutes(60 + factShiftMinutes));

        return job;
    }
}