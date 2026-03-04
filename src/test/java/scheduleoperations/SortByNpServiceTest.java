package scheduleoperations;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.SortRangeRequest;
import org.acme.foodpackaging.scheduleoperations.SortByNpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    // --- sortRangeByNp ---

    private SortRangeRequest sortRangeRequest(String lineId, int fromIndex, int sortCount, boolean sortUp) {
        SortRangeRequest req = new SortRangeRequest();
        req.setLineId(lineId);
        req.setFromIndex(fromIndex);
        req.setSortCount(sortCount);
        req.setSortUp(sortUp);
        return req;
    }

    @Test
    void sortRangeByNp_sortUp_sortsRangeAscendingByNp() {
        Job j1 = job("J1", 30, vanilla);
        Job j2 = job("J2", 10, vanilla);
        Job j3 = job("J3", 20, vanilla);
        line1.setJobs(new ArrayList<>(List.of(j1, j2, j3)));
        schedule.setJobs(List.of(j1, j2, j3));

        service.sortRangeByNp(schedule, sortRangeRequest("L1", 0, 3, true));

        assertEquals(List.of(10, 20, 30), line1.getJobs().stream().map(Job::getNp).toList());
    }

    @Test
    void sortRangeByNp_sortDown_sortsRangeDescendingByNp() {
        Job j1 = job("J1", 10, vanilla);
        Job j2 = job("J2", 30, vanilla);
        Job j3 = job("J3", 20, vanilla);
        line1.setJobs(new ArrayList<>(List.of(j1, j2, j3)));
        schedule.setJobs(List.of(j1, j2, j3));

        service.sortRangeByNp(schedule, sortRangeRequest("L1", 0, 3, false));

        assertEquals(List.of(30, 20, 10), line1.getJobs().stream().map(Job::getNp).toList());
    }

    @Test
void sortRangeByNp_partialRange_sortsOnlySpecifiedSublist() {
    Job j1 = job("J1", 1, vanilla);
    Job j2 = job("J2", 50, vanilla);
    Job j3 = job("J3", 20, vanilla);
    Job j4 = job("J4", 5, vanilla);
    Job j5 = job("J5", 9, vanilla);

    line1.setJobs(new ArrayList<>(List.of(j1, j2, j3, j4, j5)));
    schedule.setJobs(line1.getJobs());

    service.sortRangeByNp(schedule, sortRangeRequest("L1", 1, 4, true));

    // indices 1..4 sorted ascending: 5,9,20,50; первый элемент j1 не изменился
    assertEquals(List.of(1, 5, 9, 20, 50),
            line1.getJobs().stream().map(Job::getNp).toList());
}

    @Test
    void sortRangeByNp_otherLinesUnaffected() {
        Job a1 = job("A1", 3, vanilla);
        Job a2 = job("A2", 1, vanilla);
        Job a3 = job("A3", 2, vanilla);
        Job b1 = job("B1", 9, vanilla);
        Job b2 = job("B2", 7, vanilla);
        line1.setJobs(new ArrayList<>(List.of(a1, a2, a3)));
        line2.setJobs(new ArrayList<>(List.of(b1, b2)));
        schedule.setJobs(List.of(a1, a2, a3, b1, b2));

        service.sortRangeByNp(schedule, sortRangeRequest("L1", 0, 3, true));

        assertEquals(List.of(1, 2, 3), line1.getJobs().stream().map(Job::getNp).toList());
        assertEquals(List.of(9, 7), line2.getJobs().stream().map(Job::getNp).toList());
    }

@Test
void sortRangeByNp_truncatesRangeIfTooLong() {
    Job j1 = job("J1", 1, vanilla);
    Job j2 = job("J2", 2, vanilla);
    line1.setJobs(new ArrayList<>(List.of(j1, j2)));
    schedule.setJobs(List.of(j1, j2));

    SortRangeRequest request = sortRangeRequest("L1", 0, 5, true);

    // метод больше не выбрасывает исключение
    service.sortRangeByNp(schedule, request);

    // сортировка проходит по существующему диапазону
    assertEquals(List.of(1, 2),
            line1.getJobs().stream().map(Job::getNp).toList());
}

@Test
void sortRangeByNp_truncatesRangeWhenFromIndexCloseToEnd() {
    Job j1 = job("J1", 1, vanilla);
    Job j2 = job("J2", 2, vanilla);
    line1.setJobs(new ArrayList<>(List.of(j1, j2)));
    schedule.setJobs(List.of(j1, j2));

    SortRangeRequest request = sortRangeRequest("L1", 1, 2, true);

    service.sortRangeByNp(schedule, request);

    // сортируется только j2, первый элемент не меняется
    assertEquals(List.of(1, 2),
            line1.getJobs().stream().map(Job::getNp).toList());
}
   
    @Test
    void sortRangeByNp_singleElementRange_unchanged() {
        Job j1 = job("J1", 42, vanilla);
        line1.setJobs(new ArrayList<>(List.of(j1)));
        schedule.setJobs(List.of(j1));

        service.sortRangeByNp(schedule, sortRangeRequest("L1", 0, 1, true));

        assertEquals(List.of(42), line1.getJobs().stream().map(Job::getNp).toList());
    }

    @Test
    void sortRangeByNp_fullRangeSortDown() {
        Job j1 = job("J1", 5, vanilla);
        Job j2 = job("J2", 2, vanilla);
        Job j3 = job("J3", 8, vanilla);
        line1.setJobs(new ArrayList<>(List.of(j1, j2, j3)));
        schedule.setJobs(List.of(j1, j2, j3));

        service.sortRangeByNp(schedule, sortRangeRequest("L1", 0, 3, false));

        assertEquals(List.of(8, 5, 2), line1.getJobs().stream().map(Job::getNp).toList());
    }

@Test
void sortRangeByNp_removesExtensionsAndSorts() {
    // обычные задачи с np
    Job j1 = job("J1", 3, vanilla);
    Job j2 = job("J2", 1, vanilla);
    Job j3 = job("J3", 2, vanilla);

    // extension/packaging (maintenance 7/8)
    Job pack7 = job("Pack7", 100, vanilla);
    pack7.setMaintenance(true);
    pack7.setMaintenanceTypeId(7);

    Job pack8 = job("Pack8", 50, vanilla);
    pack8.setMaintenance(true);
    pack8.setMaintenanceTypeId(8);

    // исходная линия
    line1.setJobs(new ArrayList<>(List.of(j1, pack7, j2, j3, pack8)));
    schedule.setJobs(line1.getJobs());

    SortRangeRequest request = new SortRangeRequest();
    request.setLineId(line1.getId());
    request.setFromIndex(0);
    request.setSortCount(5); 
    request.setSortUp(true);

    service.sortRangeByNp(schedule, request);

    List<Job> jobs = line1.getJobs();

    assertFalse(jobs.contains(pack7));
    assertFalse(jobs.contains(pack8));

    assertEquals(3, jobs.size());

    assertEquals("J2", jobs.get(0).getName()); // np=1
    assertEquals("J3", jobs.get(1).getName()); // np=2
    assertEquals("J1", jobs.get(2).getName()); // np=3
}
}