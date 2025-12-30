package scheduleOperations;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.MoveJobsRequestDTO;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleOperations.MoveJobsService;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MoveJobsServiceTest {

    @InjectMocks
    MoveJobsService service;

    @Mock
    LoadDataService loadDataService;

    PackagingSchedule schedule;
    Line line1;
    Line line2;
    Product productA;
    Product productB;

    @BeforeEach
    void setUp() {
        productA = new Product("A", "A");
        productB = new Product("B", "B");
        productA.setType("TYPE_A");
        productB.setType("TYPE_B");

        line1 = new Line("line1", "Line 1");
        line2 = new Line("line2", "Line 2");

        schedule = new PackagingSchedule();
        schedule.setLines(List.of(line1, line2));
        schedule.setJobs(new ArrayList<>());

        // speed cache
        SpeedCacheUtils.init(Map.of(
                "line1", Map.of("TYPE_A", 10, "TYPE_B", 10),
                "line2", Map.of("TYPE_A", 15, "TYPE_B", 20)
        ));
    }

    private Job job(String id, String name, Product product) {
        return new Job(
                id, name, product,
                Duration.ofMinutes(10),
                null, null, null,
                1, false, null, null
        );
    }

    @Test
    void movingOnSameLine() {
        Job j1 = job("1", "J1", productA);
        Job j2 = job("2", "J2", productA);
        Job j3 = job("3", "J3", productA);

        line1.setJobs(new ArrayList<>(List.of(j1, j2, j3)));
        schedule.getJobs().addAll(line1.getJobs());

        MoveJobsRequestDTO request = new MoveJobsRequestDTO();
        request.setFromLineId("line1");
        request.setToLineId("line1");
        request.setFromIndex(0);
        request.setCount(1);
        request.setInsertIndex(2);

        service.moveJobs(schedule, request);

        assertEquals(
                List.of("J2", "J3", "J1"),
                line1.getJobs().stream().map(Job::getName).toList()
        );
    }

    @Test
    void movingInsideRange() {
        Job j1 = job("1", "J1", productA);
        Job j2 = job("2", "J2", productA);

        line1.setJobs(new ArrayList<>(List.of(j1, j2)));

        MoveJobsRequestDTO request = new MoveJobsRequestDTO();
        request.setFromLineId("line1");
        request.setToLineId("line1");
        request.setFromIndex(0);
        request.setCount(2);
        request.setInsertIndex(1);

        service.moveJobs(schedule, request);

        assertEquals(List.of(j1, j2), line1.getJobs());
    }

    @Test
    void movingBetweenLines() {
        Job j1 = job("1", "J1", productA);

        line1.setJobs(new ArrayList<>(List.of(j1)));
        line2.setJobs(new ArrayList<>());

        MoveJobsRequestDTO request = new MoveJobsRequestDTO();
        request.setFromLineId("line1");
        request.setToLineId("line2");
        request.setFromIndex(0);
        request.setCount(1);
        request.setInsertIndex(0);

        service.moveJobs(schedule, request);

        assertTrue(line1.getJobs().isEmpty());
        assertEquals(1, line2.getJobs().size());
        assertEquals("J1", line2.getJobs().getFirst().getName());
    }

    @Test
    void sanityCheck() {
        assertNotNull(service);
        assertNotNull(loadDataService);
    }
}
