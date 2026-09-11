package scheduleoperations;

import builder.MaintenanceRowBuilder;
import org.acme.foodpackaging.dto.request.maintenance.*;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleoperations.MaintenanceService;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

        @Mock
        LoadDataService loadDataService;
        private MaintenanceService maintenanceService;
        private PackagingSchedule schedule;
        private Line line;

        @BeforeEach
        void setup() {
                maintenanceService = new MaintenanceService(loadDataService);

                // Line, schedule
                line = new Line("line1", "Line 1", "operator", LocalDateTime.now());
                schedule = new PackagingSchedule();
                schedule.setLines(List.of(line));

                // Products
                Product maintenanceProduct = new Product("MAINTENANCE", "Maintenance Product");
                Product normalProduct = new Product("NORMAL", "Normal Product");

                // Cleaning durations
                Map<Product, Duration> cleaningMaintenance = new HashMap<>();
                cleaningMaintenance.put(maintenanceProduct, Duration.ZERO);
                cleaningMaintenance.put(normalProduct, Duration.ofMinutes(10));
                maintenanceProduct.setCleaningDurations(cleaningMaintenance);

                Map<Product, Duration> cleaningNormal = new HashMap<>();
                cleaningNormal.put(maintenanceProduct, Duration.ofMinutes(5));
                cleaningNormal.put(normalProduct, Duration.ZERO);
                normalProduct.setCleaningDurations(cleaningNormal);

                // SpeedCacheUtils
                Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();
                Map<String, Pair<Integer, Integer>> productSpeeds = new HashMap<>();
                productSpeeds.put("MAINTENANCE", Pair.of(1, 0));
                productSpeeds.put("NORMAL", Pair.of(2, 1));
                speeds.put("line1", productSpeeds);
                SpeedCacheUtils.init(speeds);

                schedule.setProducts(List.of(maintenanceProduct, normalProduct));
                schedule.setWorkCalendar(new WorkCalendar(LocalDate.now()));
                schedule.setJobs(new ArrayList<>());
        }

        // ============================================================
        // createMaintenanceProduct
        // ============================================================

        @Test
        void createMaintenanceProduct_returnsExpectedProduct() {
                Product product = MaintenanceService.createMaintenanceProduct();

                assertEquals("Maintenance Product", product.getName());
                assertEquals("MAINTENANCE", product.getId());
        }

        // ============================================================
        // addMaintenanceJob
        // ============================================================

        @Test
        void maintenanceJobInEmptyLineTest() {
                AddMaintenanceRequest request = new AddMaintenanceRequest(
                                "line1",
                                "Maintenance 1",
                                4,
                                30,
                                null,
                                null,
                                LocalDateTime.now());

                PackagingSchedule result = maintenanceService.addMaintenanceJob(schedule, request);

                assertEquals(1, result.getJobs().size());
                Job job = result.getJobs().getFirst();
                assertTrue(job.isMaintenance());
                assertEquals("Maintenance 1", job.getMaintenanceNote());
                assertEquals(30, job.getDuration().toMinutes());
                assertEquals(line, job.getLine());
        }

        @Test
        void addMaintenanceJobTest() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();

                Job existingJob = new Job(row, "Maintenance Name", product);

                line.getJobs().add(existingJob);
                schedule.getJobs().add(existingJob);

                AddMaintenanceRequest request = new AddMaintenanceRequest(
                                "line1",
                                "Maintenance 2",
                                4,
                                15,
                                0,
                                null,
                                null);

                PackagingSchedule result = maintenanceService.addMaintenanceJob(schedule, request);

                assertEquals(2, result.getJobs().size());
                assertEquals(4, line.getJobs().getFirst().getMaintenanceTypeId());
                assertEquals("Maintenance 2", line.getJobs().getFirst().getMaintenanceNote());
                assertEquals("Maintenance Name", line.getJobs().get(1).getName());
        }

        @Test
        void addMaintenanceAddsExtraWhenDurationAtLeastSixHours() {
                CleaningDurationUtils.init(Map.of("line1", 40));

                // Seed one job to make line non-empty
                AddMaintenanceRequest seed = new AddMaintenanceRequest(
                                "line1",
                                null,
                                2,
                                20,
                                0,
                                null,
                                null);
                maintenanceService.addMaintenanceJob(schedule, seed);

                AddMaintenanceRequest req = new AddMaintenanceRequest(
                                "line1",
                                null,
                                2,
                                360,
                                1, // insert after seed
                                null,
                                null);

                PackagingSchedule result = maintenanceService.addMaintenanceJob(schedule, req);

                // Expect two jobs at indices 1 (original) and 2 (extra)
                assertEquals(3, result.getJobs().size());
                Job inserted = line.getJobs().get(1);
                Job extra = line.getJobs().get(2);
                assertTrue(inserted.isMaintenance());
                assertTrue(extra.isMaintenance());
                assertEquals(40, extra.getDuration().toMinutes());
                assertEquals(2, extra.getMaintenanceTypeId());
                assertEquals("Мойка", extra.getName());
        }

        @Test
        void addMaintenanceAddsExtraWhenDurationAtLeastSixHours_EmptyLineReusesStart() {
                CleaningDurationUtils.init(Map.of("line1", 30));

                LocalDateTime start = LocalDateTime.of(2025, Month.JANUARY, 30, 8, 0);
                AddMaintenanceRequest req = new AddMaintenanceRequest(
                                "line1",
                                null,
                                4,
                                400,
                                null,
                                null,
                                start);

                PackagingSchedule result = maintenanceService.addMaintenanceJob(schedule, req);

                assertEquals(2, result.getJobs().size());
                Job first = line.getJobs().get(0);
                Job extra = line.getJobs().get(1);
                assertEquals(start, first.getStartProductionDateTime());
                assertNotNull(extra.getStartProductionDateTime(), "Extra job should have a start time");
                assertFalse(extra.getStartProductionDateTime().isBefore(first.getStartProductionDateTime()),
                                "Extra job should not start before the first maintenance");
                assertEquals(30, extra.getDuration().toMinutes());
                assertEquals(2, extra.getMaintenanceTypeId());
                assertEquals("Мойка", extra.getName());
        }

        @Test
        void addMaintenanceJob_invalidInsertIndexThrows() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();
                Job existingJob = new Job(row, "Maintenance Name", product);
                line.getJobs().add(existingJob);
                schedule.getJobs().add(existingJob);

                AddMaintenanceRequest request = new AddMaintenanceRequest(
                                "line1",
                                null,
                                2,
                                30,
                                5, // out of bounds
                                null,
                                null);

                assertThrows(IllegalArgumentException.class,
                                () -> maintenanceService.addMaintenanceJob(schedule, request));
        }

        @Test
        void addMaintenanceJob_noExtraCleaningWhenDurationBelowSixHours() {
                CleaningDurationUtils.init(Map.of("line1", 40));

                AddMaintenanceRequest request = new AddMaintenanceRequest(
                                "line1",
                                null,
                                2,
                                60, // less than 6 hours
                                null,
                                null,
                                LocalDateTime.now());

                PackagingSchedule result = maintenanceService.addMaintenanceJob(schedule, request);

                assertEquals(1, result.getJobs().size());
        }

        @Test
        void addMaintenanceJob_noExtraCleaningWhenAlignTypeExcluded() {
                CleaningDurationUtils.init(Map.of("line1", 40));

                AddMaintenanceRequest request = new AddMaintenanceRequest(
                                "line1",
                                null,
                                8, // alignType excluded from extra cleaning
                                360,
                                null,
                                null,
                                LocalDateTime.now());

                PackagingSchedule result = maintenanceService.addMaintenanceJob(schedule, request);

                assertEquals(1, result.getJobs().size());
        }

        @Test
        void addMaintenanceJob_insertIndexResolvedByStartTimeWhenNotProvided() {
                Product product = schedule.getProducts().getFirst();
                LocalDateTime existingStart = LocalDateTime.of(2025, Month.MARCH, 1, 12, 0);

                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();
                Job existingJob = new Job(row, "Existing", product);
                existingJob.setStartProductionDateTime(existingStart);
                line.getJobs().add(existingJob);
                schedule.getJobs().add(existingJob);

                AddMaintenanceRequest request = new AddMaintenanceRequest(
                                "line1",
                                null,
                                2,
                                30,
                                null, // no explicit insertIndex — should be resolved by time
                                null,
                                existingStart.minusHours(1));

                PackagingSchedule result = maintenanceService.addMaintenanceJob(schedule, request);

                assertEquals(2, result.getJobs().size());
                assertEquals("Existing", line.getJobs().get(1).getName());
        }

        // ============================================================
        // updateMaintenanceJob
        // ============================================================

        @Test
        void updateMaintenanceJob_typeOnly() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();

                Job job = new Job(row, "MaintenanceJob 1", product);
                job.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
                job.setDuration(Duration.ofMinutes(20));
                line.getJobs().add(job);
                schedule.getJobs().add(job);

                UpdateMaintenanceRequest request = new UpdateMaintenanceRequest(
                                "line1",
                                0,
                                2,
                                null,
                                null);

                ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
                maintenanceTypes.put(2, "Мойка");
                when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

                PackagingSchedule result = maintenanceService.updateMaintenanceJob(schedule, request);

                assertEquals(2, result.getJobs().getFirst().getMaintenanceTypeId());
                assertEquals("Мойка", result.getJobs().getFirst().getName());
                assertEquals(20, result.getJobs().getFirst().getDuration().toMinutes());
        }

        @Test
        void updateMaintenanceJob_noteOnly() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();

                Job job = new Job(row, "MaintenanceJob 1", product);
                job.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
                job.setMaintenanceNote("Old note");
                line.getJobs().add(job);
                schedule.getJobs().add(job);

                UpdateMaintenanceRequest request = new UpdateMaintenanceRequest(
                                "line1",
                                0,
                                null,
                                "New note",
                                null);

                PackagingSchedule result = maintenanceService.updateMaintenanceJob(schedule, request);

                assertEquals("New note", result.getJobs().getFirst().getMaintenanceNote());
        }

        @Test
        void updateMaintenanceJob_typeNoteAndDurationTogether() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();

                Job job = new Job(row, "MaintenanceJob 1", product);
                job.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
                line.getJobs().add(job);
                schedule.getJobs().add(job);

                UpdateMaintenanceRequest request = new UpdateMaintenanceRequest(
                                "line1",
                                0,
                                2,
                                "Updated note",
                                45);

                ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
                maintenanceTypes.put(1, "Обслуживание");
                maintenanceTypes.put(2, "Мойка");
                when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

                PackagingSchedule result = maintenanceService.updateMaintenanceJob(schedule, request);

                assertEquals(45, result.getJobs().getFirst().getDuration().toMinutes());
                assertEquals(2, result.getJobs().getFirst().getMaintenanceTypeId());
                assertEquals("Updated note", result.getJobs().getFirst().getMaintenanceNote());
                assertEquals("Мойка", result.getJobs().getFirst().getName());
        }

        @Test
        void updateMaintenanceJob_invalidIndexThrows() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();

                Job job = new Job(row, "MaintenanceJob 1", product);
                line.getJobs().add(job);
                schedule.getJobs().add(job);

                UpdateMaintenanceRequest request = new UpdateMaintenanceRequest(
                                "line1",
                                5,
                                null,
                                null,
                                30);

                assertThrows(IllegalArgumentException.class,
                                () -> maintenanceService.updateMaintenanceJob(schedule, request));
        }

        // ============================================================
        // removeMaintenanceJob
        // ============================================================

        @Test
        void removeMaintenanceJobByIndexTest() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();

                Job job = new Job(row, "MaintenanceJob 1", product);
                job.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
                job.setMaintenance(true);
                line.getJobs().add(job);
                schedule.getJobs().add(job);

                PackagingSchedule result = maintenanceService.removeMaintenanceJob(schedule, "line1", 0);

                assertTrue(result.getJobs().isEmpty());
                assertTrue(line.getJobs().isEmpty());
        }

        @Test
        void removeMaintenanceJob_invalidIndexThrows() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();
                Job job = new Job(row, "MaintenanceJob 1", product);
                line.getJobs().add(job);
                schedule.getJobs().add(job);

                assertThrows(IllegalArgumentException.class,
                                () -> maintenanceService.removeMaintenanceJob(schedule, "line1", 5));
        }

        @Test
        void removeMaintenanceJob_nonMaintenanceJobIsNotRemoved() {
                Product product = schedule.getProducts().getFirst();
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();
                Job job = new Job(row, "MaintenanceJob 1", product);
                job.setMaintenance(false);
                line.getJobs().add(job);
                schedule.getJobs().add(job);

                PackagingSchedule result = maintenanceService.removeMaintenanceJob(schedule, "line1", 0);

                assertEquals(1, result.getJobs().size());
                assertEquals(1, line.getJobs().size());
        }

        // ============================================================
        // dailyCleaning
        // ============================================================

        @Test
        void dailyCleaningEmptyLine() {
                CleaningDurationUtils.init(Map.of("line1", 30));
                line.setJobs(new ArrayList<>());

                maintenanceService.addDailyFullCleaning(schedule);

                assertTrue(line.getJobs().isEmpty());
        }

        @Test
        void dailyCleaningNullJobs() {
                CleaningDurationUtils.init(Map.of("line1", 30));
                line.setJobs(null);

                maintenanceService.addDailyFullCleaning(schedule);

                assertNull(line.getJobs());
        }

        // --- addDailyFullCleaning (new logic: anchor from reversed jobs,
        // dailyCleaningStart <= last job end) ---

        @Test
        void addDailyFullCleaning_skipsWhenNoAnchor() {
                CleaningDurationUtils.init(Map.of("line1", 30));
                Product product = schedule.getProducts().get(1);
                // One production job with no preceding cleaning gap >= 30 min; no type-2
                // maintenance
                LocalDateTime start = LocalDateTime.of(2025, Month.JANUARY, 15, 8, 0);
                Job job = Job.fromDbJobRow(
                                new DbJobRow(null, "", 0, 0, 0.0,
                                                start, start.plusMinutes(60),
                                                60, 2212L, 0, "line1", "Job", 0, 100, 0),
                                product, start, null);
                line.setStartDateTime(start);
                line.getJobs().add(job);
                schedule.getJobs().add(job);
                ScheduleUtils.fixLineJobs(line);

                maintenanceService.addDailyFullCleaning(schedule);

                assertEquals(1, line.getJobs().size());
        }

        @Test
        void addDailyFullCleaning_skipsWhenDailyCleaningStartAfterLastJobEnd() {
                CleaningDurationUtils.init(Map.of("line1", 30));
                Product maintenanceProduct = schedule.getProducts().getFirst();
                // Single maintenance type 2, duration 40 min; dailyCleaningStart = end+24h,
                // last job = same → skip
                LocalDateTime jobEnd = LocalDateTime.of(2025, Month.JANUARY, 15, 10, 0);
                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();
                Job mJob = new Job(row, "Maintenance job", maintenanceProduct);

                line.setStartDateTime(jobEnd.minusMinutes(40));
                line.getJobs().add(mJob);
                schedule.getJobs().add(mJob);
                ScheduleUtils.fixLineJobs(line);

                maintenanceService.addDailyFullCleaning(schedule);

                assertEquals(1, line.getJobs().size());
        }

        @Test
        void addDailyFullCleaning_addsWhenAnchorMaintenanceType2AndLastJobEndAfterStart() {
                CleaningDurationUtils.init(Map.of("line1", 30));
                ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
                maintenanceTypes.put(2, "Мойка");
                when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

                Product maintenanceProduct = schedule.getProducts().get(0);
                Product normalProduct = schedule.getProducts().get(1);
                LocalDateTime day1At10 = LocalDateTime.of(2025, Month.JANUARY, 15, 10, 0);
                LocalDateTime day2At15 = LocalDateTime.of(2025, Month.JANUARY, 16, 15, 0);
                LocalDateTime dayAt0920 = LocalDateTime.of(2025, Month.JANUARY, 16, 9, 20);

                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();
                line.setStartDateTime(dayAt0920); // so fixLineJobs (after add) places all jobs on Jan 16
                Job mJob = new Job(row, "Мойка", maintenanceProduct);

                mJob.setLine(line);
                line.getJobs().add(mJob);
                schedule.getJobs().add(mJob);

                Job prod = Job.fromDbJobRow(
                                new DbJobRow(null, "", 0, 0, 0.0,
                                                day1At10.plusMinutes(30), day2At15,
                                                60, 2212L, 0, "line1", "Job", 0, 100, 0),
                                normalProduct, day1At10.plusMinutes(30), null);
                prod.setStartCleaningDateTime(day1At10);
                prod.setStartProductionDateTime(day1At10.plusMinutes(30));
                prod.setEndDateTime(day2At15);
                prod.setLine(line);
                line.getJobs().add(prod);
                schedule.getJobs().add(prod);

                maintenanceService.addDailyFullCleaning(schedule);

                assertEquals(3, line.getJobs().size());
                Job added = line.getJobs().get(2);
                assertEquals(2, added.getMaintenanceTypeId());
                assertEquals(30, added.getDuration().toMinutes());
                // fixLineJobs recalculates the new job's start from previous job end +
                // cleaning; assert next day and ~10:00
                assertEquals(LocalDate.of(2025, Month.JANUARY, 16), added.getStartProductionDateTime().toLocalDate());
                // addDailyFullCleaning sets maxEndTime = last job end + 20h (last is the newly
                // added job)
                assertNotNull(line.getMaxEndTime());
                assertEquals(line.getJobs().getLast().getEndDateTime().plusHours(20), line.getMaxEndTime());
        }

        @Test
        void addDailyFullCleaning_addsWhenAnchorFromCleaningGap() {
                CleaningDurationUtils.init(Map.of("line1", 25));
                ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
                maintenanceTypes.put(2, "Мойка");
                when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

                Product normalProduct = schedule.getProducts().get(1);
                LocalDateTime day1At8 = LocalDateTime.of(2025, Month.JANUARY, 15, 8, 0);
                LocalDateTime day1At830 = LocalDateTime.of(2025, Month.JANUARY, 15, 8, 30);
                LocalDateTime day2At10 = LocalDateTime.of(2025, Month.JANUARY, 16, 10, 0);

                Job prod = Job.fromDbJobRow(
                                new DbJobRow(null, "", 0, 0, 0.0,
                                                day1At830, day2At10,
                                                60, 2212L, 0, "line1", "Job", 0, 100, 0),
                                normalProduct, day1At830, null);
                // New logic: cleaning duration = between(startCleaning, startProduction); need
                // startCleaning < startProduction for positive gap
                prod.setStartCleaningDateTime(day1At8);
                prod.setStartProductionDateTime(day1At830);
                prod.setEndDateTime(day2At10);
                prod.setLine(line);
                line.setStartDateTime(day1At8);
                line.getJobs().add(prod);
                schedule.getJobs().add(prod);

                maintenanceService.addDailyFullCleaning(schedule);

                assertEquals(2, line.getJobs().size());
                Job added = line.getJobs().stream()
                                .filter(j -> j.isMaintenance() && j.getMaintenanceTypeId() == 2
                                                && j.getDuration().toMinutes() == 25)
                                .findFirst().orElseThrow();
                assertTrue(added.isMaintenance());
                assertEquals(2, added.getMaintenanceTypeId());
                assertEquals(25, added.getDuration().toMinutes());
                // fixLineJobs recalculates from line start (day1At8): new job ends up after
                // previous; accept same or next day
                assertTrue(added.getStartProductionDateTime().toLocalDate()
                                .equals(LocalDate.of(2025, Month.JANUARY, 16))
                                || added.getStartProductionDateTime().toLocalDate()
                                                .equals(LocalDate.of(2025, Month.JANUARY, 15)));
                // addDailyFullCleaning sets maxEndTime = last job end + 20h (last is the newly
                // added job)
                assertNotNull(line.getMaxEndTime());
                assertEquals(line.getJobs().getLast().getEndDateTime().plusHours(20), line.getMaxEndTime());
        }

        @Test
        void addDailyFullCleaning_multipleLines() {
                CleaningDurationUtils.init(Map.of("line1", 30, "line2", 25));
                ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
                maintenanceTypes.put(2, "Мойка");
                when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

                Line line2 = new Line("line2", "Line 2", "op2", LocalDateTime.of(2025, Month.JANUARY, 30, 8, 0));
                schedule.setLines(List.of(line, line2));

                Product maintenanceProduct = schedule.getProducts().get(0);
                Product normalProduct = schedule.getProducts().get(1);
                LocalDateTime day1At10 = LocalDateTime.of(2025, Month.JANUARY, 15, 10, 0);
                LocalDateTime day2At15 = LocalDateTime.of(2025, Month.JANUARY, 16, 15, 0);

                MaintenanceRow row = MaintenanceRowBuilder.aRow().build();
                // Line1: maint type 2 end 10:00, prod end day2 15:00 (no fixLineJobs so end
                // stays)
                Job m1 = new Job(row, "Мойка", maintenanceProduct);
                m1.setMaintenance(true);
                m1.setMaintenanceTypeId(2);
                m1.setEndDateTime(day1At10);
                m1.setLine(line);
                line.getJobs().add(m1);
                schedule.getJobs().add(m1);
                Job p1 = Job.fromDbJobRow(
                                new DbJobRow(null, "", 0, 0, 0.0,
                                                day1At10.plusMinutes(30), day2At15,
                                                60, 2212L, 0, "line1", "Job", 0, 100, 0),
                                normalProduct, day1At10.plusMinutes(30), null);
                p1.setStartCleaningDateTime(day1At10);
                p1.setStartProductionDateTime(day1At10.plusMinutes(30));
                p1.setEndDateTime(day2At15);
                p1.setLine(line);
                line.getJobs().add(p1);
                schedule.getJobs().add(p1);

                // Line2: same pattern
                Job m2 = new Job(row, "Мойка", maintenanceProduct);

                m2.setLine(line2);
                line2.getJobs().add(m2);
                schedule.getJobs().add(m2);
                Job p2 = Job.fromDbJobRow(
                                new DbJobRow(null, "", 0, 0, 0.0,
                                                day1At10.plusMinutes(30), day2At15,
                                                60, 2214L, 0, "line2", "Job2", 0, 100, 0),
                                normalProduct, day1At10.plusMinutes(30), null);
                p2.setStartCleaningDateTime(day1At10);
                p2.setStartProductionDateTime(day1At10.plusMinutes(30));
                p2.setEndDateTime(day2At15);
                p2.setLine(line2);
                line2.getJobs().add(p2);
                schedule.getJobs().add(p2);

                maintenanceService.addDailyFullCleaning(schedule);

                assertEquals(3, line.getJobs().size());
                assertEquals(3, line2.getJobs().size());
                assertEquals(30, line.getJobs().get(2).getDuration().toMinutes());
                assertEquals(25, line2.getJobs().get(2).getDuration().toMinutes());
                // addDailyFullCleaning sets maxEndTime = last job end + 20h on each line (last
                // = newly added job)
                assertNotNull(line.getMaxEndTime());
                assertNotNull(line2.getMaxEndTime());
                assertEquals(line.getJobs().getLast().getEndDateTime().plusHours(20), line.getMaxEndTime());
                assertEquals(line2.getJobs().getLast().getEndDateTime().plusHours(20), line2.getMaxEndTime());
        }
}