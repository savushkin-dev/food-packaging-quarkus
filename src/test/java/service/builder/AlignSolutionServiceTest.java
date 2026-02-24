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

        Job job = createPlanJob("J1",
                LocalDateTime.of(2025,1,15,10,0),
                60,
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

        Job job = createPlanJob("J1",
                LocalDateTime.of(2025,1,15,10,0),
                60,
                60);

        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    // ============================================================
    // alignLineStartByFact (новая логика)
    // ============================================================

    @Test
    void alignLineStartByFact_whenFactAfterPlan_shouldAddMaintenance() {

        Product product = createProduct("P1");

        Job j1 = createChainJob("J1", product, 1,
                LocalDateTime.of(2025,1,15,10,0),
                20);

        Job j2 = createChainJob("J2", product, 2,
                LocalDateTime.of(2025,1,15,11,0),
                20);

        line.getJobs().addAll(List.of(j1, j2));
        schedule.getJobs().addAll(List.of(j1, j2));

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> captor =
                ArgumentCaptor.forClass(MaintenanceRequest.class);

        verify(maintenanceJob).addMaintenanceJob(eq(schedule), captor.capture());

        assertEquals(20, captor.getValue().getDurationMinutes());
        assertEquals(8, captor.getValue().getMaintenanceTypeId());
    }

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
    void alignLineStartByFact_whenAlignMaintenanceExists_shouldUpdate() {

        Product product = createProduct("P1");

        Job align = new Job();
        align.setMaintenance(true);
        align.setMaintenanceTypeId(8);
        align.setStartProductionDateTime(
                LocalDateTime.of(2025,1,15,9,0)
        );
        align.setDuration(Duration.ofMinutes(60)); // ⚠ обязательно

        Job j1 = createChainJob("J1", product, 1,
                LocalDateTime.of(2025,1,15,9,30),
                20);

        j1.setPreviousJob(align);

        Job j2 = createChainJob("J2", product, 2,
                LocalDateTime.of(2025,1,15,10,30),
                20);

        line.getJobs().addAll(List.of(align, j1, j2));
        schedule.getJobs().addAll(List.of(align, j1, j2));

        alignSolutionService.alignLineStartByFact(schedule);

        verify(maintenanceJob).updateDuration(eq(schedule), any());
    }

    @Test
    void alignLineStartByFact_whenSameProductInTwoSegments_usesLastChain() {
        Product p1 = createProduct("P1");
        Product p2 = createProduct("P2");
        Job j1 = createChainJob("J1", p1, 1, LocalDateTime.of(2025, 1, 15, 10, 0), 0);
        Job j2 = createChainJob("J2", p2, 1, LocalDateTime.of(2025, 1, 15, 11, 0), 0);
        Job j3 = createChainJob("J3", p1, 2, LocalDateTime.of(2025, 1, 15, 12, 0), 10);
        line.getJobs().addAll(List.of(j1, j2, j3));
        schedule.getJobs().addAll(List.of(j1, j2, j3));

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> captor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), captor.capture());
        assertEquals(10, captor.getValue().getDurationMinutes());
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
    void alignLineStartByFact_whenFactStartBeforeAlignStart_shouldClampDurationAndUpdate() {
        Product product = createProduct("P1");
        Job align = new Job();
        align.setMaintenance(true);
        align.setMaintenanceTypeId(8);
        align.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        align.setDuration(Duration.ofMinutes(60));

        Job j1 = createChainJob("J1", product, 1, LocalDateTime.of(2025, 1, 15, 10, 0), -30);
        j1.setPreviousJob(align);
        Job j2 = createChainJob("J2", product, 2, LocalDateTime.of(2025, 1, 15, 11, 0), -30);
        line.getJobs().addAll(List.of(align, j1, j2));
        schedule.getJobs().addAll(List.of(align, j1, j2));

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> captor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).updateDuration(eq(schedule), captor.capture());
        assertEquals(0, captor.getValue().getDurationMinutes());
    }

    // ============================================================
    // helpers
    // ============================================================

    private Product createProduct(String id) {
        Product p = new Product();
        p.setId(id); // ⚠ id обязательно для цепочек
        return p;
    }

    private Job createPlanJob(String id,
                              LocalDateTime planStart,
                              long planMinutes,
                              long factMinutes) {

        Job job = new Job();
        job.setId(id);
        job.setStartProductionDateTime(planStart);
        job.setEndDateTime(planStart.plusMinutes(planMinutes));

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

        job.setStartProductionDateTime(planStart);
        job.setEndDateTime(planStart.plusMinutes(60));

        job.setCameraStart(planStart.plusMinutes(factShiftMinutes));
        job.setCameraEnd(planStart.plusMinutes(60 + factShiftMinutes));

        return job;
    }
}