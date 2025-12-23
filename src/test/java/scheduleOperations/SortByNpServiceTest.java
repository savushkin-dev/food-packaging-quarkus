package scheduleOperations;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.scheduleOperations.SortByNpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SortByNpServiceTest {

    private SortByNpService service;

    private Product vanilla;
    private Line line1;
    private Line line2;
    private PackagingSchedule schedule;

    @BeforeEach
    void setUp() {
        service = new SortByNpService();

        vanilla = new Product("VAN", "Vanilla");

        line1 = new Line("L1", "Line 1");
        line2 = new Line("L2", "Line 2");

        schedule = new PackagingSchedule();
        schedule.setLines(List.of(line1, line2));
    }

    private Job job(String name, int np, Product product) {
        Job j = new Job();
        j.setName(name);
        j.setNp(np);
        j.setProduct(product);
        j.setMaintenance(false);
        j.setLineId(null);
        return j;
    }

    @Test
    void sortJobsByNpOnSingleLine() {
        Job j1 = job("J1", 3, vanilla);
        Job j2 = job("J2", 1, vanilla);
        Job j3 = job("J3", 2, vanilla);

        line1.setJobs(List.of(j1, j2, j3));
        line2.setJobs(List.of());

        schedule.setJobs(List.of(j1, j2, j3));

        service.reorderJobsByProductNp(schedule);

        assertEquals(
                List.of(1,2,3),
                line1.getJobs().stream().map(Job::getNp).toList()
        );
    }

    @Test
    void alternateOrderForSameProductOnDifferentLines() {
        // line 1
        Job v1 = job("V1", 1, vanilla);
        Job v2 = job("V2", 2, vanilla);
        Job v3 = job("V3", 3, vanilla);

        // line 2
        Job v4 = job("V4", 4, vanilla);
        Job v5 = job("V5", 5, vanilla);
        Job v6 = job("V6", 6, vanilla);

        line1.setJobs(List.of(v3, v1, v2)); // намеренно вразнобой
        line2.setJobs(List.of(v6, v4, v5));

        schedule.setJobs(List.of(v3, v1, v2, v6, v4, v5));

        service.reorderJobsByProductNp(schedule);

        // 1-е появление продукта → возрастающий NP
        assertEquals(
                List.of(1, 2, 3),
                line1.getJobs().stream().map(Job::getNp).toList()
        );

        // 2-е появление продукта → убывающий NP
        assertEquals(
                List.of(6, 5, 4),
                line2.getJobs().stream().map(Job::getNp).toList()
        );
    }

    @Test
    void notReorderMaintenanceJobs() {
        Job j1 = job("J1", 2, vanilla);
        Job j2 = job("J2", 1, vanilla);

        Job maintenance = new Job();
        maintenance.setName("CLEAN");
        maintenance.setMaintenance(true);
        maintenance.setLineId("L1");

        line1.setJobs(List.of(j1, maintenance, j2));
        line2.setJobs(List.of());

        schedule.setJobs(List.of(j1, maintenance, j2));

        service.reorderJobsByProductNp(schedule);

        assertEquals(
                List.of("J2", "CLEAN", "J1"),
                line1.getJobs().stream().map(Job::getName).toList()
        );
    }
}