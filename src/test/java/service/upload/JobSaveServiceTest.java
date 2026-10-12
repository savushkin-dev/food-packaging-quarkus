package service.upload;

import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.ParallelOperation;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.BdVpmcRepository;
import org.acme.foodpackaging.repository.jobs.MsLogRepository;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;
import org.acme.foodpackaging.service.upload.JobSaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSaveServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, Month.FEBRUARY, 24, 9, 0);

    @InjectMocks
    JobSaveService jobSaveService;

    @Mock
    BdVpmcRepository bdVpmcRepository;
    @Mock
    OeePevRepository oeePevRepository;
    @Mock
    MsLogRepository msLogRepository;

    private PackagingSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());
    }

    @Test
    void save_newParallelOperation_persistsAndReplacesIdWithFId() {
        ParallelOperation operation = operation("generated-key");
        schedule.setParallelOperations(Map.of(operation.getId(), operation));
        doAnswer(invocation -> {
            invocation.<OeePev>getArgument(0).setFId(55L);
            return null;
        }).when(oeePevRepository).persist(any(OeePev.class));

        jobSaveService.saveJobsByType(schedule);

        ArgumentCaptor<OeePev> captor = ArgumentCaptor.forClass(OeePev.class);
        verify(oeePevRepository).persist(captor.capture());
        OeePev entity = captor.getValue();
        assertEquals("L1", entity.getLineId());
        assertEquals(START, entity.getStartDateTime());
        assertEquals(START.plusMinutes(90), entity.getEndDateTime());
        assertEquals(90, entity.getDuration());
        assertEquals(3, entity.getEventTypeId());
        assertEquals("note", entity.getNote());
        assertEquals(OeePev.PARALLEL_OPERATION_SNPZ, entity.getSnpz());

        Map<String, ParallelOperation> saved = schedule.getParallelOperations();
        assertEquals(1, saved.size());
        assertEquals("55", saved.get("55").getId());
    }

    @Test
    void save_existingParallelOperation_updatesRowAndKeepsId() {
        ParallelOperation operation = operation("55");
        schedule.setParallelOperations(Map.of(operation.getId(), operation));
        OeePev existing = OeePev.builder().fId(55L).snpz(OeePev.PARALLEL_OPERATION_SNPZ).lineId("L2").build();
        when(oeePevRepository.findByFId(55L)).thenReturn(existing);

        jobSaveService.saveJobsByType(schedule);

        verify(oeePevRepository, never()).persist(any(OeePev.class));
        assertEquals("L1", existing.getLineId());
        assertEquals(90, existing.getDuration());
        assertSame(operation, schedule.getParallelOperations().get("55"));
    }

    @Test
    void save_numericIdOfForeignRow_persistsNewRowInsteadOfOverwriting() {
        ParallelOperation operation = operation("55");
        schedule.setParallelOperations(Map.of(operation.getId(), operation));
        OeePev maintenanceRow = OeePev.builder().fId(55L).snpz(0L).lineId("L2").build();
        when(oeePevRepository.findByFId(55L)).thenReturn(maintenanceRow);

        jobSaveService.saveJobsByType(schedule);

        verify(oeePevRepository).persist(any(OeePev.class));
        assertEquals("L2", maintenanceRow.getLineId());
    }

    @Test
    void save_deletedParallelOperation_marksRowDeletedOnce() {
        schedule.setDeletedParallelOperations(List.of(operation("55"), operation("generated-key")));
        OeePev existing = OeePev.builder().fId(55L).snpz(OeePev.PARALLEL_OPERATION_SNPZ).build();
        when(oeePevRepository.findByFId(55L)).thenReturn(existing);

        jobSaveService.saveJobsByType(schedule);

        assertEquals(1, existing.getFDel());
        assertTrue(schedule.getDeletedParallelOperations().isEmpty());
        verify(oeePevRepository, never()).persist(any(OeePev.class));
    }

    private ParallelOperation operation(String id) {
        return ParallelOperation.builder()
                .id(id)
                .lineId("L1")
                .name("Мойка")
                .startDateTime(START)
                .duration(Duration.ofMinutes(90))
                .endDateTime(START.plusMinutes(90))
                .maintenanceTypeId(3)
                .maintenanceNote("note")
                .build();
    }
}
