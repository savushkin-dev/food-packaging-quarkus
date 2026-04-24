package align;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.align.AlignByLastChainService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AlignByLastChainServiceTest {

    @InjectMocks
    AlignByLastChainService alignLastChain;

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
        product.setCleaningDurations(Map.of(product, Duration.ZERO));
        j1.setProduct(product);

        return j1;
    }

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

        alignLastChain.alignLineStartByFact(solution);

        Mockito.verify(maintenanceJob, Mockito.never()).addMaintenanceJob(ArgumentMatchers.any(), ArgumentMatchers.any());
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

        alignLastChain.alignLineStartByFact(solution);

        Mockito.verify(maintenanceJob, Mockito.never()).updateDuration(ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.verify(maintenanceJob, Mockito.never()).addMaintenanceJob(ArgumentMatchers.any(), ArgumentMatchers.any());
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

        alignLastChain.alignLineStartByFact(solution);

        Mockito.verify(maintenanceJob).updateDuration(ArgumentMatchers.eq(solution), ArgumentMatchers.any());
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

        alignLastChain.alignLineStartByFact(solution);

        ArgumentCaptor<MaintenanceRequest> captor =
                ArgumentCaptor.forClass(MaintenanceRequest.class);

        Mockito.verify(maintenanceJob).addMaintenanceJob(ArgumentMatchers.eq(solution), captor.capture());

        MaintenanceRequest request = captor.getValue();
        Assertions.assertEquals(8, request.getMaintenanceTypeId());
        Assertions.assertTrue(request.getDurationMinutes() > 0);
        Assertions.assertEquals(line.getMaxEndTime(), line.getJobs().getLast().getEndDateTime().plusHours(20));
    }

    @Test
    void alignLineStartByFact_whenPlanHasCleaning_shouldSetExtraMinutes() {

        Product product = createProduct();

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

        alignLastChain.alignLineStartByFact(solution);

        ArgumentCaptor<MaintenanceRequest> captor =
                ArgumentCaptor.forClass(MaintenanceRequest.class);

        Mockito.verify(maintenanceJob).addMaintenanceJob(ArgumentMatchers.eq(solution), captor.capture());

        MaintenanceRequest request = captor.getValue();

        Assertions.assertEquals(20, request.getAlignExtraCleaning());
        Assertions.assertEquals(40, request.getDurationMinutes());
    }

    // ============================================================
    // helpers
    // ============================================================

    private Product createProduct() {
        Product p = new Product();
        p.setId("P1");
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

        job.setStartCleaningDateTime(planStart);
        job.setStartProductionDateTime(planStart);

        job.setEndDateTime(planStart.plusMinutes(60));

        job.setCameraStart(planStart.plusMinutes(factShiftMinutes));
        job.setCameraEnd(planStart.plusMinutes(60 + factShiftMinutes));

        return job;
    }
}
