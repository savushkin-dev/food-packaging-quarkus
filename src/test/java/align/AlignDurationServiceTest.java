package align;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.align.AlignDurationService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

@ExtendWith(MockitoExtension.class)
class AlignDurationServiceTest {

    @InjectMocks
    AlignDurationService alignDuration;

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
        j1.setLineIdFact("line1");

        j1.setCameraStart(LocalDateTime.of(2026,3, 6, 11, 0));
        j1.setCameraEnd(LocalDateTime.of(2026,3, 6, 11, 50));

        Job j2 = getPackagingMaintenance();


        List<Job> jobs = new ArrayList<>(List.of(j1, j2));

        line.setJobs(jobs);
        line.setStartDateTime(LocalDateTime.of(2026,3, 6, 10, 0));

        solution.setJobs(jobs);
        solution.setLines(new ArrayList<>(List.of(line, line1, line2)));

        alignDuration.alignByFactDuration(solution);

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

        alignDuration.alignByFactDuration(solution);

        assertEquals(1, solution.getJobs().size());
        assertEquals(1, solution.getLines().getFirst().getJobs().size());
        assertEquals(33, solution.getJobs().getFirst().getDuration().toMinutes());
        assertEquals(LocalDateTime.of(2026, 3, 6, 10, 33), solution.getJobs().getFirst().getEndDateTime());
        assertEquals(LocalDateTime.of(2026, 3, 6, 10, 33), solution.getJobs().getFirst().getPlanEndDateTime());
        assertNull(solution.getJobs().getFirst().getDelayDuration());
        assertFalse(solution.getJobs().getFirst().isFinalDuration());
    }

    @Test
    void alignByFactDuration_emptyJobs(){
        solution.getJobs().clear();
        alignDuration.alignByFactDuration(solution);
        assertTrue(solution.getJobs().isEmpty());
    }

    @Test
    void alignByFactDuration_NullJobs(){
        solution = new PackagingSchedule();
        alignDuration.alignByFactDuration(solution);
        assertNull(solution.getJobs());
    }

    @Test
    void alignByFactDuration_WhenMaintenanceTypeIdIsNull(){

        solution.getLines().clear();
        solution.getJobs().clear();

        Job j1 = getPackagingMaintenance();
        j1.setMaintenanceTypeId(null);

        solution.setJobs(List.of(j1));
        alignDuration.alignByFactDuration(solution);

        assertEquals(1,solution.getJobs().size());
    }

    @Test
    void alignByFactDuration_WhenJobIsNotMaintenance(){

        solution.getLines().clear();
        solution.getJobs().clear();

        Job j1 = getPackagingMaintenance();

        j1.setMaintenance(false);
        solution.setJobs(List.of(j1));
        alignDuration.alignByFactDuration(solution);

        assertEquals(1, solution.getJobs().size());
    }

    @Test
    void alignByFactDuration_WhenMaintenanceTypeIdIsNotNull(){

        solution.getLines().clear();
        solution.getJobs().clear();

        Job j1 = getPackagingMaintenance();

        solution.setJobs(new ArrayList<>(List.of(j1)));
        alignDuration.alignByFactDuration(solution);

        assertTrue(solution.getJobs().isEmpty());
    }

    @Test
    void alignByFactDuration_nullMaintenanceTypeId(){
        Job j1 = getPackagingMaintenance();
        Job j2 = getPackagingMaintenance();

        j1.setMaintenanceTypeId(null);
        j2.setMaintenanceTypeId(null);

        line.setJobs(new ArrayList<>(List.of(j1, j2)));
        solution.setJobs(new ArrayList<>(List.of(j1, j2)));

        alignDuration.alignByFactDuration(solution);

        assertFalse(solution.getJobs().isEmpty());
    }



    @Test
    void findTimeIntersections_whenLineIdFactIsNull(){
        solution.setJobs(null);
        Job j1 = new Job();
        LocalDateTime cameraStart = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 3, 9, 10, 30);

        j1.setCameraStart(cameraStart);
        j1.setCameraEnd(cameraEnd);

        j1.setStartProductionDateTime(cameraStart);
        j1.setEndDateTime(cameraEnd);

        j1.setLineIdFact(null);

        line.setJobs(List.of(j1));
        alignDuration.alignByFactDuration(solution);

        assertNull(line.getJobs().getFirst().getLineIdFact());
        assertNull(line.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void findTimeIntersections_whenLineJobsListIsNull(){
        solution.setJobs(null);
        Job j1 = new Job();
        LocalDateTime cameraStart = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 3, 9, 10, 30);

        j1.setCameraStart(cameraStart);
        j1.setCameraEnd(cameraEnd);

        j1.setStartProductionDateTime(cameraStart);
        j1.setEndDateTime(cameraEnd);
        j1.setLineIdFact("L2");

        line.setJobs(List.of(j1));
        j1.setLine(line);
        alignDuration.alignByFactDuration(solution);

        assertEquals("L2", line.getJobs().getFirst().getLineIdFact());
        assertEquals("line1", line.getId());
        assertNull(line.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void findTimeIntersections(){
        solution.setJobs(null);
        Job j1 = getJob();
        Job j2 = getJob();

        LocalDateTime cameraStart1 = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd1 = LocalDateTime.of(2026, 3, 9, 11, 20);

        LocalDateTime cameraStart2 = LocalDateTime.of(2026, 3, 9, 10, 10);
        LocalDateTime cameraEnd2 = LocalDateTime.of(2026, 3, 9, 10, 40);

        j1.setCameraStart(cameraStart1);
        j1.setCameraEnd(cameraEnd1);

        j1.setStartProductionDateTime(cameraStart1);
        j1.setEndDateTime(cameraEnd1.minusMinutes(30));
        j1.setLineIdFact("line1");

        j2.setCameraStart(cameraStart2);
        j2.setCameraEnd(cameraEnd2);
        j2.setStartProductionDateTime(j2.getCameraStart());
        j2.setEndDateTime(j2.getCameraEnd());
        j2.setLineIdFact("line1");

        line.setJobs(List.of(j1, j2));
        line.setStartDateTime(cameraStart1);
        j1.setLine(line);
        j2.setLine(line);
        solution.setLines(List.of(line));
        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertEquals(17, line.getJobs().getFirst().getDelayDuration().toMinutes());
        assertEquals(LocalDateTime.of(2026, 3, 9, 10, 50), line.getJobs().getFirst().getEndDateTime());
        assertEquals(LocalDateTime.of(2026, 3, 9, 10, 33), line.getJobs().getFirst().getPlanEndDateTime());
        assertEquals(50, line.getJobs().getFirst().getDuration().toMinutes());

        assertFalse(line.getJobs().getLast().isFinalDuration());
        assertNull(line.getJobs().getLast().getDelayDuration());
        assertEquals(LocalDateTime.of(2026, 3, 9, 11, 23),line.getJobs().getLast().getPlanEndDateTime());
        assertEquals(LocalDateTime.of(2026, 3, 9, 11, 23), line.getJobs().getLast().getEndDateTime());
    }

    @Test
    void noIntersection_before() {
        Job j1 = getJob();
        Job j2 = getJob();

        j1.setCameraStart(LocalDateTime.of(2026, 3, 9, 10, 0));
        j1.setCameraEnd(LocalDateTime.of(2026, 3, 9, 11, 0));

        j2.setCameraStart(LocalDateTime.of(2026, 3, 9, 9, 0));
        j2.setCameraEnd(LocalDateTime.of(2026, 3, 9, 9, 59));

        j1.setLineIdFact("line1");
        j2.setLineIdFact("line1");
        j1.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 10, 0));
        j2.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 9, 0));

        line.setStartDateTime(LocalDateTime.of(2026, 3, 9, 9, 0));
        line.setJobs(new ArrayList<>(List.of(j1,j2)));

        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertEquals(Duration.ofMinutes(60), line.getJobs().getFirst().getDuration());

        assertTrue(line.getJobs().getLast().isFinalDuration());
        assertEquals(Duration.ofMinutes(59), line.getJobs().getLast().getDuration());
    }

    @Test
    void noIntersection_after() {
        Job j1 = getJob();
        Job j2 = getJob();

        j1.setCameraStart(LocalDateTime.of(2026, 3, 9, 10, 0));
        j1.setCameraEnd(LocalDateTime.of(2026, 3, 9, 11, 0));

        j2.setCameraStart(LocalDateTime.of(2026, 3, 9, 11, 1));
        j2.setCameraEnd(LocalDateTime.of(2026, 3, 9, 12, 0));

        j1.setLineIdFact("line1");
        j2.setLineIdFact("line1");
        j1.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 10, 0));
        j2.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 11, 1));

        line.setStartDateTime(LocalDateTime.of(2026, 3, 9, 9, 0));
        line.setJobs(new ArrayList<>(List.of(j1,j2)));

        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertEquals(Duration.ofMinutes(60), line.getJobs().getFirst().getDuration());

        assertTrue(line.getJobs().getLast().isFinalDuration());
        assertEquals(Duration.ofMinutes(59), line.getJobs().getLast().getDuration());
    }

    @Test
    void touchingBoundary_shouldNotIntersect() {
        Job j1 = getJob();
        Job j2 = getJob();

        j1.setCameraStart(LocalDateTime.of(2026, 3, 9, 10, 0));
        j1.setCameraEnd(LocalDateTime.of(2026, 3, 9, 11, 0));

        j2.setCameraStart(LocalDateTime.of(2026, 3, 9, 11, 0));
        j2.setCameraEnd(LocalDateTime.of(2026, 3, 9, 12, 59));

        j1.setLineIdFact("line1");
        j2.setLineIdFact("line1");
        j1.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 10, 0));
        j2.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 11, 0));

        line.setStartDateTime(LocalDateTime.of(2026, 3, 9, 9, 0));
        line.setJobs(new ArrayList<>(List.of(j1,j2)));

        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertEquals(Duration.ofMinutes(60), line.getJobs().getFirst().getDuration());

        assertTrue(line.getJobs().getLast().isFinalDuration());
        assertEquals(Duration.ofMinutes(119), line.getJobs().getLast().getDuration());
    }

    @Test
    void differentLine_shouldBeIgnored() {
        Job j1 = getJob();
        Job j2 = getJob();

        j1.setCameraStart(LocalDateTime.of(2026, 3, 9, 10, 0));
        j1.setCameraEnd(LocalDateTime.of(2026, 3, 9, 11, 0));
        j1.setLineIdFact("line1");

        j2.setCameraStart(LocalDateTime.of(2026, 3, 9, 10, 30));
        j2.setCameraEnd(LocalDateTime.of(2026, 3, 9, 11, 0));
        j2.setLineIdFact("line2");

        j1.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 10, 0));
        j2.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 11, 30));

        line.setStartDateTime(LocalDateTime.of(2026, 3, 9, 10, 0));
        line.setJobs(new ArrayList<>(List.of(j1,j2)));

        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertEquals(Duration.ofMinutes(60), line.getJobs().getFirst().getDuration());

        assertFalse(line.getJobs().getLast().isFinalDuration());
        assertEquals(Duration.ofMinutes(33), line.getJobs().getLast().getDuration());
    }

    @Test
    void missingCameraData_shouldBeIgnored() {
        Job j1 = getJob();
        Job j2 = getJob();

        j1.setCameraStart(LocalDateTime.of(2026, 3, 9, 10, 0));
        j1.setCameraEnd(LocalDateTime.of(2026, 3, 9, 11, 0));

        j1.setLineIdFact("line1");
        j2.setLineIdFact("line1");

        j2.setCameraStart(null);
        j2.setCameraEnd(null);

        j1.setStartProductionDateTime(LocalDateTime.of(2026, 3, 9, 10, 0));

        line.setStartDateTime(LocalDateTime.of(2026, 3, 9, 10, 0));
        line.setJobs(new ArrayList<>(List.of(j1,j2)));

        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertEquals(Duration.ofMinutes(60), line.getJobs().getFirst().getDuration());

        assertFalse(line.getJobs().getLast().isFinalDuration());
        assertEquals(Duration.ofMinutes(33), line.getJobs().getLast().getDuration());
    }

    @Test
    void findTimeIntersections_wrongLine(){
        solution.setJobs(null);
        Job j1 = getJob();
        Job j2 = getJob();
        Job j3 = getJob();

        LocalDateTime cameraStart1 = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd1 = LocalDateTime.of(2026, 3, 9, 10, 50);

        LocalDateTime cameraStart2 = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd2 = LocalDateTime.of(2026, 3, 9, 10, 40);

        j1.setCameraStart(cameraStart1);
        j1.setCameraEnd(cameraEnd1);

        j1.setStartProductionDateTime(cameraStart1);
        j1.setEndDateTime(cameraEnd1.minusMinutes(35));
        j1.setLineIdFact("line1");

        j2.setCameraStart(cameraStart2);
        j2.setCameraEnd(cameraEnd2);
        j2.setStartProductionDateTime(j2.getCameraStart());
        j2.setEndDateTime(j2.getCameraEnd().minusMinutes(20));
        j2.setLineIdFact("line2");

        line.setJobs(List.of(j1, j2, j3));
        line.setStartDateTime(cameraStart1);
        j1.setLine(line);
        j2.setLine(line);
        solution.setLines(List.of(line));
        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertEquals(17, line.getJobs().getFirst().getDelayDuration().toMinutes());
        assertEquals(LocalDateTime.of(2026, 3, 9, 10, 50), line.getJobs().getFirst().getEndDateTime());
        assertEquals(LocalDateTime.of(2026, 3, 9, 10, 33), line.getJobs().getFirst().getPlanEndDateTime());
        assertEquals(50, line.getJobs().getFirst().getDuration().toMinutes());

        assertFalse(line.getJobs().getLast().isFinalDuration());
    }

    @Test
    void findTimeIntersections_cameraDataMissing() {
        solution.setJobs(null);
        Job j1 = getJob();
        Job j2 = getJob();

        LocalDateTime cameraStart1 = LocalDateTime.of(2026, 3, 9, 10, 0);
        LocalDateTime cameraEnd1 = LocalDateTime.of(2026, 3, 9, 10, 50);

        LocalDateTime cameraStart2 = LocalDateTime.of(2026, 3, 9, 10, 10);

        j1.setCameraStart(cameraStart1);
        j1.setCameraEnd(cameraEnd1);

        j1.setStartProductionDateTime(cameraStart1);
        j1.setEndDateTime(cameraEnd1.minusMinutes(35));
        j1.setLineIdFact("line1");

        j2.setCameraStart(cameraStart2);
        j2.setCameraEnd(null);

        j2.setStartProductionDateTime(cameraStart2);
        j2.setEndDateTime(cameraStart2.plusMinutes(20));
        j2.setLineIdFact("line1");

        line.setJobs(List.of(j1, j2));
        line.setStartDateTime(cameraStart1);

        j1.setLine(line);
        j2.setLine(line);

        solution.setLines(List.of(line));

        alignDuration.alignByFactDuration(solution);

        assertTrue(line.getJobs().getFirst().isFinalDuration());
        assertFalse(line.getJobs().getLast().isFinalDuration());
    }

    @Test
    void alignByFactDuration_WhenPlanEndDateTimeIsNotNull(){
        Job j1 = getJob();

        j1.setLineIdFact(line.getId());
        j1.setLine(line);
        j1.setDuration(Duration.ofMinutes(50));
        j1.setFinalDuration(true);
        j1.setDelayDuration(Duration.ofMinutes(20));

        j1.setStartProductionDateTime(LocalDateTime.of(2026,3,31, 16,0));
        j1.setEndDateTime(LocalDateTime.of(2026,3,31, 16,50));

        j1.setCameraStart(LocalDateTime.of(2026,3,31, 16,0));
        j1.setCameraEnd(LocalDateTime.of(2026,3,31, 16,50));

        line.setStartDateTime(LocalDateTime.of(2026,3,31, 16,0));
        line.setJobs(new ArrayList<>(List.of(j1)));
        solution.setJobs(List.of(j1));
        alignDuration.alignByFactDuration(solution);

        assertEquals(LocalDateTime.of(2026,3,31, 16,33), line.getJobs().getFirst().getPlanEndDateTime());
        assertEquals(Duration.ofMinutes(17), line.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void alignByFactDuration_WhenPlanEndDateTimeIsNull(){
        Job j1 = getJob();

        j1.setLineIdFact(line.getId());
        j1.setLine(line);

        j1.setStartProductionDateTime(LocalDateTime.of(2026,3,31, 16,0));
        j1.setEndDateTime(LocalDateTime.of(2026,3,31, 16,33));

        j1.setCameraStart(LocalDateTime.of(2026,3,31, 16,0));
        j1.setCameraEnd(LocalDateTime.of(2026,3,31, 16,50));

        line.setStartDateTime(LocalDateTime.of(2026,3,31, 16,0));
        line.setJobs(new ArrayList<>(List.of(j1)));
        solution.setJobs(List.of(j1));
        alignDuration.alignByFactDuration(solution);

        assertEquals(LocalDateTime.of(2026,3,31, 16,33), line.getJobs().getFirst().getPlanEndDateTime());
        assertEquals(Duration.ofMinutes(17), line.getJobs().getFirst().getDelayDuration());
        assertEquals(Duration.ofMinutes(50), line.getJobs().getFirst().getDuration());

        assertTrue(line.getJobs().getFirst().isFinalDuration());
    }

}
