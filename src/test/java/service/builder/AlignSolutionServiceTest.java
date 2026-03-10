package service.builder;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.builder.AlignSolutionService;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private PackagingSchedule solution;
    private Line line;

    @BeforeEach
    void setUp() {
        solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>());

        line = new Line("line1", "Line 1");
        line.setJobs(new ArrayList<>());

        // Init speeds
        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();

        Map<String, Pair<Integer, Integer>> productSpeeds = new HashMap<>();
        productSpeeds.put("CLASSIC", Pair.of(100, 100));

        speeds.put("line1", productSpeeds);

        SpeedCacheUtils.init(speeds);

        solution.setLines(new ArrayList<>(List.of(line)));
    }

    private Job getPackagingMaintenance(){
        Job job = new Job();
        job.setMaintenance(true);
        job.setMaintenanceTypeId(7);
        return job;
    }

    private Job getJob() {
        Job j1 = new Job();

        j1.setLine(line);
        j1.setQuantity(2900);
        Product product = new Product();
        product.setType("CLASSIC");
        j1.setProduct(product);

        return j1;
    }

    // ============================================================
    // alignByFactDuration
    // ============================================================

    @Test
    void alignByFactDuration(){
        Job j1 = getJob();
        Line line1 = new Line();
        Line line2 = new Line();
        line1.setJobs(new ArrayList<>());
        j1.setStartCleaningDateTime(LocalDateTime.of(2026,3, 6, 10, 0));
        j1.setStartProductionDateTime(LocalDateTime.of(2026,3, 6, 10, 0));
        j1.setEndDateTime(LocalDateTime.of(2026,3, 6, 10, 33));

        j1.setCameraStart(LocalDateTime.of(2026,3, 6, 11, 0));
        j1.setCameraEnd(LocalDateTime.of(2026,3, 6, 11, 50));

        Job j2 = getPackagingMaintenance();

        List<Job> jobs = new ArrayList<>(List.of(j1, j2));

        line.setJobs(jobs);
        line.setStartDateTime(LocalDateTime.of(2026,3, 6, 10, 0));

        solution.setJobs(jobs);
        solution.setLines(new ArrayList<>(List.of(line, line1, line2)));

        alignSolutionService.alignByFactDuration(solution);

        assertEquals(1, solution.getJobs().size());
        assertEquals(1, solution.getLines().getFirst().getJobs().size());
        assertEquals(50, solution.getJobs().getFirst().getDuration().toMinutes());
        assertEquals(17, solution.getJobs().getFirst().getDelayDuration().toMinutes());
        assertEquals(LocalDateTime.of(2026,3, 6, 10, 33), solution.getJobs().getFirst().getPlanEndDateTime());
        assertEquals(LocalDateTime.of(2026, 3, 6, 10, 50), solution.getJobs().getFirst().getEndDateTime());

        assertTrue(solution.getJobs().getFirst().isFinalDuration());
        assertTrue(solution.getLines().get(1).getJobs().isEmpty());

        assertNull(solution.getLines().get(2).getJobs());
    }

    @Test
    void alignByFactDuration_NullCameraData(){
        Job j1 = getJob();
        j1.setStartCleaningDateTime(LocalDateTime.of(2026,3, 6, 10, 0));
        j1.setStartProductionDateTime(LocalDateTime.of(2026,3, 6, 10, 0));
        j1.setEndDateTime(LocalDateTime.of(2026,3, 6, 10, 33));

        Job j2 = getPackagingMaintenance();

        List<Job> jobs = new ArrayList<>(List.of(j1, j2));

        line.setJobs(jobs);
        line.setStartDateTime(LocalDateTime.of(2026,3, 6, 10, 0));
        solution.setJobs(jobs);
        solution.setLines(new ArrayList<>(List.of(line)));

        alignSolutionService.alignByFactDuration(solution);

        assertEquals(1, solution.getJobs().size());
        assertEquals(1, solution.getLines().getFirst().getJobs().size());
        assertEquals(33, solution.getJobs().getFirst().getDuration().toMinutes());
        assertEquals(LocalDateTime.of(2026, 3, 6, 10, 33), solution.getJobs().getFirst().getEndDateTime());
        assertNull(solution.getJobs().getFirst().getDelayDuration());
        assertNull(solution.getJobs().getFirst().getPlanEndDateTime());
        assertFalse(solution.getJobs().getFirst().isFinalDuration());
    }

    @Test
    void alignByFactDuration_emptyJobs(){
        solution.getJobs().clear();
        alignSolutionService.alignByFactDuration(solution);
        assertTrue(solution.getJobs().isEmpty());
    }

    @Test
    void alignByFactDuration_NullJobs(){
       solution = new PackagingSchedule();
       alignSolutionService.alignByFactDuration(solution);
       assertNull(solution.getJobs());
    }
    // ============================================================
    // alignLineStartByFact
    // ============================================================

    @Test
    void alignLineStartByFact_whenFactEqualsPlan_shouldNotAddMaintenance() {

        Product product = createProduct();

        Job j1 = createChainJob("J1", product, 1,
                LocalDateTime.of(2025,1,15,10,0),
                0);

        Job j2 = createChainJob("J2", product, 2,
                LocalDateTime.of(2025,1,15,11,0),
                0);
        line.getJobs().clear();
        solution.getJobs().clear();
        line.getJobs().addAll(List.of(j1, j2));
        solution.getJobs().addAll(List.of(j1, j2));

        alignSolutionService.alignLineStartByFact(solution);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenAlignMaintenanceDurationAlreadyMatches_shouldNotUpdate() {
        Product product = createProduct();
        Job align = new Job();
        align.setMaintenance(true);
        align.setMaintenanceTypeId(8);
        align.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        align.setDuration(Duration.ofMinutes(30));

        Job j1 = createChainJob("J1", product, 1, LocalDateTime.of(2025, 1, 15, 9, 30), 0);
        j1.setPreviousJob(align);
        Job j2 = createChainJob("J2", product, 2, LocalDateTime.of(2025, 1, 15, 10, 30), 0);
        line.getJobs().clear();
        solution.getJobs().clear();
        line.getJobs().addAll(List.of(align, j1, j2));
        solution.getJobs().addAll(List.of(align, j1, j2));

        alignSolutionService.alignLineStartByFact(solution);

        verify(maintenanceJob, never()).updateDuration(any(), any());
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenAlignMaintenanceDurationDiffers_shouldUpdate() {

        Product product = createProduct();

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

        line.getJobs().clear();
        solution.getJobs().clear();
        line.getJobs().addAll(List.of(align, j1, j2));
        solution.getJobs().addAll(List.of(align, j1, j2));

        alignSolutionService.alignLineStartByFact(solution);

        verify(maintenanceJob).updateDuration(eq(solution), any());
    }

    @Test
    void alignLineStartByFact_whenPlanBeforeFact_shouldAddAlignMaintenance() {

        Product product = createProduct();

        Job j1 = createChainJob("J1", product, 1,
                LocalDateTime.of(2025, 1, 15, 9, 0), 30); // факт сдвинут на 30 мин

        Job j2 = createChainJob("J2", product, 2,
                LocalDateTime.of(2025, 1, 15, 10, 0), 30);

        line.getJobs().addAll(List.of(j1, j2));
        solution.getJobs().addAll(List.of(j1, j2));

        alignSolutionService.alignLineStartByFact(solution);

        ArgumentCaptor<MaintenanceRequest> captor =
                ArgumentCaptor.forClass(MaintenanceRequest.class);

        verify(maintenanceJob).addMaintenanceJob(eq(solution), captor.capture());

        MaintenanceRequest request = captor.getValue();
        assertEquals(8, request.getMaintenanceTypeId());
        assertTrue(request.getDurationMinutes() > 0);
    }

    @Test
    void alignLineStartByFact_whenPlanHasCleaning_shouldSetExtraMinutes() {

        Product product = createProduct();

        // === Первая задача (target) ===
        Job j1 = new Job();
        j1.setId("J1");
        j1.setProduct(product);
        j1.setNp(1);

        j1.setStartCleaningDateTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        j1.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 9, 20));
        j1.setEndDateTime(LocalDateTime.of(2025, 1, 15, 10, 20));

        j1.setCameraStart(LocalDateTime.of(2025, 1, 15, 10, 0));
        j1.setCameraEnd(LocalDateTime.of(2025, 1, 15, 11, 0));

        // === Вторая задача с тем же продуктом ===
        Job j2 = new Job();
        j2.setId("J2");
        j2.setProduct(product);
        j2.setNp(2);

        j2.setStartCleaningDateTime(LocalDateTime.of(2025, 1, 15, 10, 20));
        j2.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 10, 20));
        j2.setEndDateTime(LocalDateTime.of(2025, 1, 15, 11, 20));

        j2.setCameraStart(LocalDateTime.of(2025, 1, 15, 10, 30));
        j2.setCameraEnd(LocalDateTime.of(2025, 1, 15, 11, 30));

        line.getJobs().clear();
        solution.getJobs().clear();
        line.getJobs().addAll(List.of(j1, j2));
        solution.getJobs().addAll(List.of(j1, j2));

        alignSolutionService.alignLineStartByFact(solution);

        ArgumentCaptor<MaintenanceRequest> captor =
                ArgumentCaptor.forClass(MaintenanceRequest.class);

        verify(maintenanceJob).addMaintenanceJob(eq(solution), captor.capture());

        MaintenanceRequest request = captor.getValue();

        assertEquals(20, request.getAlignExtraCleaning());
        assertEquals(40, request.getDurationMinutes());
    }

    @Test
    void findTimeIntersections_whenLineIdFactIsNull(){
        PackagingSchedule solution1 = new PackagingSchedule();
        solution1.setJobs(null);
        Line line1 = new Line("L1", "line1");
        Job j1 = new Job();
        LocalDateTime cameraStart = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 3, 9, 10, 30);

        j1.setCameraStart(cameraStart);
        j1.setCameraEnd(cameraEnd);

        j1.setStartProductionDateTime(cameraStart);
        j1.setEndDateTime(cameraEnd);

        j1.setLineIdFact(null);

        line1.setJobs(List.of(j1));
        solution1.setLines(List.of(line1));
        alignSolutionService.alignByFactDuration(solution1);

        assertNull(line1.getJobs().getFirst().getLineIdFact());
        assertNull(line1.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void findTimeIntersections_whenLineJobsListIsNull(){
        PackagingSchedule solution1 = new PackagingSchedule();
        solution1.setJobs(null);
        Line line1 = new Line("L1", "line1");
        Job j1 = new Job();
        LocalDateTime cameraStart = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 3, 9, 10, 30);

        j1.setCameraStart(cameraStart);
        j1.setCameraEnd(cameraEnd);

        j1.setStartProductionDateTime(cameraStart);
        j1.setEndDateTime(cameraEnd);
        j1.setLineIdFact("L2");

        line1.setJobs(List.of(j1));
        j1.setLine(line1);
        solution1.setLines(List.of(line1));
        alignSolutionService.alignByFactDuration(solution1);

        assertEquals("L2", line1.getJobs().getFirst().getLineIdFact());
        assertEquals("L1", line1.getId());
        assertNull(line1.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void findTimeIntersections(){
        PackagingSchedule solution1 = new PackagingSchedule();
        solution1.setJobs(null);
        Line line1 = new Line("L1", "line1");
        Job j1 = new Job();
        Job j2 = new Job();
        Product product = new Product("P1", "product1");
        product.setCleaningDurations(Map.of(product, Duration.ZERO));

        LocalDateTime cameraStart1 = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd1 = LocalDateTime.of(2026, 3, 9, 10, 40);

        LocalDateTime cameraStart2 = LocalDateTime.of(2026, 3, 9, 10, 10);
        LocalDateTime cameraEnd2 = LocalDateTime.of(2026, 3, 9, 10, 30);

        j1.setCameraStart(cameraStart1);
        j1.setCameraEnd(cameraEnd1);
        j1.setProduct(product);

        j1.setStartProductionDateTime(cameraStart1);
        j1.setEndDateTime(cameraEnd1.minusMinutes(25));
        j1.setLineIdFact("L1");

        j2.setCameraStart(cameraStart2);
        j2.setCameraEnd(cameraEnd2);
        j2.setStartProductionDateTime(j2.getCameraStart());
        j2.setEndDateTime(j2.getCameraEnd());
        j2.setProduct(product);
        j2.setLineIdFact("L1");

        line1.setJobs(List.of(j1, j2));
        line1.setStartDateTime(cameraStart1);
        j1.setLine(line1);
        j2.setLine(line1);
        solution1.setLines(List.of(line1));
        alignSolutionService.alignByFactDuration(solution1);

        assertTrue(line1.getJobs().getFirst().isFinalDuration());
        assertEquals(5, line1.getJobs().getFirst().getDelayDuration().toMinutes());
    }
    // ============================================================
    // helpers
    // ============================================================

    private Product createProduct() {
        Product p = new Product();
        p.setId("P1"); // ⚠ id обязательно для цепочек
        return p;
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