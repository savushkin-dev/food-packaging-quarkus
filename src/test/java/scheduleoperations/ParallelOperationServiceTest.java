package scheduleoperations;

import org.acme.foodpackaging.domain.ParallelOperation;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.paralleloperations.*;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleoperations.ParallelOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParallelOperationServiceTest {

    @Mock
    LoadDataService loadDataService;

    private ParallelOperationService parallelOperationService;
    private PackagingSchedule schedule;

    @BeforeEach
    void setup() {
        parallelOperationService = new ParallelOperationService(loadDataService);
        schedule = new PackagingSchedule();
    }

    // ---------- add ----------

    @Test
    void add_createsOperationWithGeneratedId() {
        ConcurrentMap<Integer, String> types = new ConcurrentHashMap<>();
        types.put(3, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(types);

        LocalDateTime start = LocalDateTime.of(2026, Month.FEBRUARY, 24, 9, 0);
        AddParallelOperationRequest request = new AddParallelOperationRequest(
                "line1",
                start,
                90,
                3,
                "note");

        PackagingSchedule result = parallelOperationService.add(schedule, request);

        assertEquals(1, result.getParallelOperations().size());
        ParallelOperation created = result.getParallelOperations().values().iterator().next();

        assertNotNull(created.getId());
        assertEquals("line1", created.getLineId());
        assertEquals("Мойка", created.getName());
        assertEquals(start, created.getStartDateTime());
        assertEquals(start.plusMinutes(90), created.getEndDateTime());
        assertEquals(90, created.getDuration().toMinutes());
        assertEquals(3, created.getEventTypeId());
        assertEquals("note", created.getNote());
    }

    @Test
    void add_generatesDifferentIdsForEachOperation() {
        AddParallelOperationRequest request1 = new AddParallelOperationRequest(
                "line1", null, null, null, null);
        AddParallelOperationRequest request2 = new AddParallelOperationRequest(
                "line1", null, null, null, null);

        parallelOperationService.add(schedule, request1);
        parallelOperationService.add(schedule, request2);

        assertEquals(2, schedule.getParallelOperations().size());
        var ids = schedule.getParallelOperations().keySet();
        assertEquals(2, ids.stream().distinct().count());
    }

    @Test
    void add_withoutStartOrDuration_endDateTimeIsNull() {
        AddParallelOperationRequest request = new AddParallelOperationRequest(
                "line1", null, null, null, null);

        PackagingSchedule result = parallelOperationService.add(schedule, request);

        ParallelOperation created = result.getParallelOperations().values().iterator().next();
        assertNull(created.getEndDateTime());
        assertNull(created.getDuration());
    }

    @Test
    void add_withoutEventTypeId_usesDefaultName() {
        AddParallelOperationRequest request = new AddParallelOperationRequest(
                "line1", null, 30, null, null);

        PackagingSchedule result = parallelOperationService.add(schedule, request);

        ParallelOperation created = result.getParallelOperations().values().iterator().next();
        assertEquals("Параллельная операция", created.getName());
    }

    @Test
    void add_withUnknownEventTypeId_usesDefaultName() {
        when(loadDataService.getMaintenanceTypes()).thenReturn(new ConcurrentHashMap<>());

        AddParallelOperationRequest request = new AddParallelOperationRequest(
                "line1", null, 30, 99, null);

        PackagingSchedule result = parallelOperationService.add(schedule, request);

        ParallelOperation created = result.getParallelOperations().values().iterator().next();
        assertEquals("Параллельная операция", created.getName());
    }

    // ---------- update ----------

    @Test
    void update_notFoundThrows() {
        UpdateParallelOperationRequest request = new UpdateParallelOperationRequest(
                "missing-id", null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> parallelOperationService.update(schedule, request));
    }

    @Test
    void update_durationOnly_recalculatesEndDateTime() {
        LocalDateTime start = LocalDateTime.of(2026, Month.FEBRUARY, 24, 9, 0);
        ParallelOperation existing = ParallelOperation.builder()
                .id("op1")
                .lineId("line1")
                .name("Мойка")
                .startDateTime(start)
                .duration(java.time.Duration.ofMinutes(30))
                .endDateTime(start.plusMinutes(30))
                .eventTypeId(2)
                .note("old note")
                .build();

        Map<String, ParallelOperation> operations = new HashMap<>();
        operations.put("op1", existing);
        schedule.setParallelOperations(operations);

        UpdateParallelOperationRequest request = new UpdateParallelOperationRequest(
                "op1", null, null, 120, null, null);

        PackagingSchedule result = parallelOperationService.update(schedule, request);

        ParallelOperation updated = result.getParallelOperations().get("op1");
        assertEquals(120, updated.getDuration().toMinutes());
        assertEquals(start.plusMinutes(120), updated.getEndDateTime());
        // untouched fields stay the same
        assertEquals("line1", updated.getLineId());
        assertEquals("old note", updated.getNote());
        assertEquals(2, updated.getEventTypeId());
    }

    @Test
    void update_startDateTimeOnly_recalculatesEndDateTime() {
        LocalDateTime start = LocalDateTime.of(2026, Month.FEBRUARY, 24, 9, 0);
        ParallelOperation existing = ParallelOperation.builder()
                .id("op1")
                .lineId("line1")
                .startDateTime(start)
                .duration(java.time.Duration.ofMinutes(60))
                .endDateTime(start.plusMinutes(60))
                .build();
        schedule.getParallelOperations().put("op1", existing);

        LocalDateTime newStart = LocalDateTime.of(2026, Month.MARCH, 1, 10, 0);
        UpdateParallelOperationRequest request = new UpdateParallelOperationRequest(
                "op1", null, newStart, null, null, null);

        PackagingSchedule result = parallelOperationService.update(schedule, request);

        ParallelOperation updated = result.getParallelOperations().get("op1");
        assertEquals(newStart, updated.getStartDateTime());
        assertEquals(newStart.plusMinutes(60), updated.getEndDateTime());
    }

    @Test
    void update_eventTypeId_updatesNameFromLoadDataService() {
        ConcurrentMap<Integer, String> types = new ConcurrentHashMap<>();
        types.put(5, "Наладка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(types);

        ParallelOperation existing = ParallelOperation.builder()
                .id("op1")
                .lineId("line1")
                .name("Old name")
                .eventTypeId(1)
                .build();
        schedule.getParallelOperations().put("op1", existing);

        UpdateParallelOperationRequest request = new UpdateParallelOperationRequest(
                "op1", null, null, null, 5, null);

        PackagingSchedule result = parallelOperationService.update(schedule, request);

        ParallelOperation updated = result.getParallelOperations().get("op1");
        assertEquals(5, updated.getEventTypeId());
        assertEquals("Наладка", updated.getName());
    }

    @Test
    void update_lineIdAndNoteOnly_doesNotTouchDurationOrStart() {
        LocalDateTime start = LocalDateTime.of(2026, Month.FEBRUARY, 24, 9, 0);
        ParallelOperation existing = ParallelOperation.builder()
                .id("op1")
                .lineId("line1")
                .startDateTime(start)
                .duration(java.time.Duration.ofMinutes(45))
                .endDateTime(start.plusMinutes(45))
                .note("old")
                .build();
        schedule.getParallelOperations().put("op1", existing);

        UpdateParallelOperationRequest request = new UpdateParallelOperationRequest(
                "op1", "line2", null, null, null, "new note");

        PackagingSchedule result = parallelOperationService.update(schedule, request);

        ParallelOperation updated = result.getParallelOperations().get("op1");
        assertEquals("line2", updated.getLineId());
        assertEquals("new note", updated.getNote());
        assertEquals(start, updated.getStartDateTime());
        assertEquals(45, updated.getDuration().toMinutes());
        assertEquals(start.plusMinutes(45), updated.getEndDateTime());
    }

    @Test
    void update_noFieldsProvided_keepsOperationUnchanged() {
        LocalDateTime start = LocalDateTime.of(2026, Month.FEBRUARY, 24, 9, 0);
        ParallelOperation existing = ParallelOperation.builder()
                .id("op1")
                .lineId("line1")
                .name("Мойка")
                .startDateTime(start)
                .duration(java.time.Duration.ofMinutes(30))
                .endDateTime(start.plusMinutes(30))
                .eventTypeId(2)
                .note("note")
                .build();
        schedule.getParallelOperations().put("op1", existing);

        UpdateParallelOperationRequest request = new UpdateParallelOperationRequest(
                "op1", null, null, null, null, null);

        PackagingSchedule result = parallelOperationService.update(schedule, request);

        ParallelOperation updated = result.getParallelOperations().get("op1");
        assertEquals(existing.getLineId(), updated.getLineId());
        assertEquals(existing.getName(), updated.getName());
        assertEquals(existing.getStartDateTime(), updated.getStartDateTime());
        assertEquals(existing.getDuration(), updated.getDuration());
        assertEquals(existing.getEndDateTime(), updated.getEndDateTime());
        assertEquals(existing.getEventTypeId(), updated.getEventTypeId());
        assertEquals(existing.getNote(), updated.getNote());
    }

    // ---------- remove ----------

    @Test
    void remove_existingOperation_removesFromMap() {
        ParallelOperation existing = ParallelOperation.builder()
                .id("op1")
                .lineId("line1")
                .build();
        schedule.getParallelOperations().put("op1", existing);

        PackagingSchedule result = parallelOperationService.remove(schedule, "op1");

        assertTrue(result.getParallelOperations().isEmpty());
    }

    @Test
    void remove_notFoundThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> parallelOperationService.remove(schedule, "missing-id"));
    }
}