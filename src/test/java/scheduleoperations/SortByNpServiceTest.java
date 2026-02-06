package scheduleoperations;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.scheduleoperations.SortByNpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.acme.foodpackaging.scheduleoperations.MaintenanceJob.createMaintenanceProduct;
import static org.junit.jupiter.api.Assertions.*;

class SortByNpServiceTest {

    private SortByNpService service;

    private Product vanilla;
    private Line line1;
    private Line line2;
    private Line line3;
    private Line line4;
    private Line line5;
    private Line line6;
    private PackagingSchedule schedule;

    @BeforeEach
    void setUp() {
        service = new SortByNpService();

        vanilla = new Product("VAN", "Vanilla");

        line1 = new Line("L1", "Line 1");
        line2 = new Line("L2", "Line 2");
        line3 = new Line("L3", "Line 3");
        line4 = new Line("L4", "Line 4");
        line5 = new Line("L5", "Line 5");
        line6 = new Line("L6", "Line 6");

        schedule = new PackagingSchedule();
        schedule.setLines(List.of(line1, line2, line3, line4, line5, line6));
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
        Job j1 = job("J1", 1, vanilla);
        Job j2 = job("J2", 2, vanilla);
        Job j3 = job("J3", 3, vanilla);
        Job j4 = job("J4", 4, vanilla);

        Job maintenance = new Job();
        maintenance.setName("MAINTENANCE");
        maintenance.setProduct(createMaintenanceProduct());
        maintenance.setMaintenance(true);
        maintenance.setLineId("L1");

        line1.setJobs(List.of(j3, j2, maintenance, j1,j4));
        line2.setJobs(List.of());

        schedule.setJobs(List.of(j3, j2, maintenance, j1,j4));

        service.reorderJobsByProductNp(schedule);

        assertEquals(
                List.of("J1", "J2", "MAINTENANCE", "J3", "J4"),
                line1.getJobs().stream().map(Job::getName).toList()
        );
    }

    @Test
    void notReorderJobsAcrossCleaning() {
        Job j1 = job("J1", 2, vanilla);
        Job j2 = job("J2", 1, vanilla);

        Job cleaning = new Job();
        cleaning.setName("CLEAN");
        cleaning.setMaintenance(false);
        cleaning.setLineId("L1");

        cleaning.setStartCleaningDateTime(LocalDateTime.of(2025, 1, 1, 8, 0));
        cleaning.setStartProductionDateTime(LocalDateTime.of(2025, 1, 1, 9, 0));

        line1.setJobs(List.of(j1, cleaning, j2));
        line2.setJobs(List.of());

        schedule.setJobs(List.of(j1, cleaning, j2));

        service.reorderJobsByProductNp(schedule);

        assertEquals(
                List.of("J2", "CLEAN", "J1"),
                line1.getJobs().stream().map(Job::getName).toList()
        );
    }

    @Test
    void OrderAcrossMultipleLinesForSingleProduct() {
        Job j1 = job("J1", 1, vanilla);
        Job j2 = job("J2", 2, vanilla);
        Job j3 = job("J3", 3, vanilla);
        Job j4 = job("J4", 4, vanilla);
        Job j5 = job("J5", 5, vanilla);
        Job j6 = job("J6", 6, vanilla);
        Job j7 = job("J7", 7, vanilla);
        Job j8 = job("J8", 8, vanilla);
        Job j9 = job("J9", 9, vanilla);
        Job j10 = job("J10", 10, vanilla);
        Job j11 = job("J11", 11, vanilla);
        Job j12 = job("J12", 12, vanilla);
        Job j13 = job("J13", 13, vanilla);

        line1.setJobs(List.of(j1, j2, j3));
        line2.setJobs(List.of(j4, j5));
        line3.setJobs(List.of(j6, j7, j8));
        line4.setJobs(List.of(j9, j10));
        line5.setJobs(List.of(j11, j12));
        line6.setJobs(List.of(j13));

        schedule.setJobs(List.of(j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13));

        service.reorderJobsByProductNp(schedule);

        assertEquals(List.of(1, 2, 3), line1.getJobs().stream().map(Job::getNp).toList());
        assertEquals(List.of(5, 4), line2.getJobs().stream().map(Job::getNp).toList());
        assertEquals(List.of(6, 7, 8), line3.getJobs().stream().map(Job::getNp).toList());
        assertEquals(List.of(10, 9), line4.getJobs().stream().map(Job::getNp).toList());
        assertEquals(List.of(11, 12), line5.getJobs().stream().map(Job::getNp).toList());
        assertEquals(List.of(13), line6.getJobs().stream().map(Job::getNp).toList());
    }

    @Test
    void onlyUnassignedJobsAreSorted() {

        // Линии 1,2 — часть задач unassigned (lineId == null), часть assigned
        Job j1 = job("J1", 12, vanilla); j1.setLineId("1");
        Job j2 = job("J2", 14, vanilla); j2.setLineId("1");
        Job j3 = job("J3", 24, vanilla);
        Job j4 = job("J4", 27, vanilla);
        line1.setJobs(List.of(j1, j2, j3, j4));

        Job j5 = job("J5", 10, vanilla); j5.setLineId("2");
        Job j6 = job("J6", 3, vanilla);  j6.setLineId("2");
        Job j7 = job("J7", 53, vanilla);
        Job j8 = job("J8", 54, vanilla);
        line2.setJobs(List.of(j5, j6, j7, j8));

        schedule.setJobs(List.of(j1,j2,j3,j4,j5,j6,j7,j8));
        schedule.setLines(List.of(line1, line2));

        service.reorderJobsByProductNp(schedule);

        // Проверяем, что на линии 1 порядок не изменился
        assertEquals(List.of(12,14,24,27),
                line1.getJobs().stream().map(Job::getNp).toList());

        // На линии 2 только новые загруженные задачи из пула должны быть отсортированы
        // Проверим конкретно NP последовательность, assigned задачи остаются на месте
        assertEquals(List.of(10,3,54,53),
                line2.getJobs().stream().map(Job::getNp).toList());
    }
}