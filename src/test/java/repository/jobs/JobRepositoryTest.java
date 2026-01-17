package repository.jobs;

import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobRepository.
 * Tests that JobRepository correctly delegates to JobDBLoader and transforms dates.
 * 
 * Note: SQL/native query testing is done in JobDBLoaderIT.
 * JobRepository is a thin wrapper that delegates to JobDBLoader.
 */
@ExtendWith(MockitoExtension.class)
class JobRepositoryTest {

    @Mock
    JobDBLoader jobDBLoader;

    @InjectMocks
    JobRepository jobRepository;

    private LocalDate testDate;

    @BeforeEach
    void setUp() throws Exception {
        testDate = LocalDate.of(2026, 1, 15);
        
        // Set ksk config property via reflection (since @ConfigProperty requires CDI)
        Field kskField = JobRepository.class.getDeclaredField("ksk");
        kskField.setAccessible(true);
        kskField.set(jobRepository, "test");
    }

    @Test
    void getFactProductionRowMap_DelegatesToJobDBLoader() {
        LocalDate from = testDate.minusDays(1);
        LocalDate to = testDate.plusDays(3);
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atStartOfDay();
        
        Map<FactKey, FactProductionRow> expectedResult = new HashMap<>();
        FactKey key = new FactKey("KMC1", 1);
        LocalDateTime now = LocalDateTime.now();
        FactProductionRow row = new FactProductionRow(
                "KMC1",                              // kmc
                Timestamp.valueOf(now),              // dtv
                1,                                   // np
                1,                                   // eventType
                Timestamp.valueOf(now),              // startProductionDateTimeFact
                "L1"                                 // lineIdFact
        );
        expectedResult.put(key, row);
        
        when(jobDBLoader.loadFactProductionRowMap(fromDateTime, toDateTime))
                .thenReturn(expectedResult);

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(from, to);

        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(jobDBLoader).loadFactProductionRowMap(fromDateTime, toDateTime);
    }

    @Test
    void getFactProductionRowMap_ConvertsDatesCorrectly() {
        LocalDate from = LocalDate.of(2024, 6, 20);
        LocalDate to = LocalDate.of(2024, 6, 25);
        LocalDateTime expectedFrom = from.atStartOfDay();
        LocalDateTime expectedTo = to.atStartOfDay();
        
        when(jobDBLoader.loadFactProductionRowMap(any(), any()))
                .thenReturn(new HashMap<>());

        jobRepository.getFactProductionRowMap(from, to);

        verify(jobDBLoader).loadFactProductionRowMap(expectedFrom, expectedTo);
    }


    @Test
    void getDbJobRowMap_DelegatesToJobDBLoader() {
        LocalDate from = testDate.minusDays(1);
        LocalDate to = testDate.plusDays(3);
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atStartOfDay();
        
        Map<Long, DbJobRow> expectedResult = new HashMap<>();
        DbJobRow row = new DbJobRow(
                Timestamp.valueOf(LocalDateTime.now()),  // dti
                "KMC1",                                  // kmc
                1,                                       // np
                10,                                      // quantity
                100.0,                                   // mass
                null,                                    // startProductionDateTime
                null,                                    // endDateTime
                60,                                      // duration
                1L,                                      // snpz
                1,                                       // priority
                "L1",                                    // lineId
                "Product 1"                              // shortName
        );
        expectedResult.put(1L, row);
        
        when(jobDBLoader.loadJobRowMap(fromDateTime, toDateTime, "test"))
                .thenReturn(expectedResult);

        Map<Long, DbJobRow> result = jobRepository.getDbJobRowMap(from, to);

        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(jobDBLoader).loadJobRowMap(fromDateTime, toDateTime, eq("test"));
    }

    @Test
    void getDbJobRowMap_ConvertsDatesAndPassesKsk() {
        LocalDate from = LocalDate.of(2024, 6, 20);
        LocalDate to = LocalDate.of(2024, 6, 25);
        LocalDateTime expectedFrom = from.atStartOfDay();
        LocalDateTime expectedTo = to.atStartOfDay();
        
        when(jobDBLoader.loadJobRowMap(any(), any(), anyString()))
                .thenReturn(new HashMap<>());

        jobRepository.getDbJobRowMap(from, to);

        verify(jobDBLoader).loadJobRowMap(expectedFrom, expectedTo, eq("test"));
    }

    @Test
    void getDbMaintenanceRowMap_DelegatesToJobDBLoader() {
        LocalDate from = testDate.minusDays(1);
        LocalDate to = testDate.plusDays(3);
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atStartOfDay();
        
        Map<Long, DbMaintenanceRow> expectedResult = new HashMap<>();
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusHours(2);
        DbMaintenanceRow row = new DbMaintenanceRow(
                1L,                    // fId
                (short) 0,             // fDel
                "L1",                  // lineId
                Timestamp.valueOf(startTime),  // startProductionDateTime
                Timestamp.valueOf(endTime),     // endDateTime
                120,                   // duration
                0L,                    // snpz
                "Maintenance 1"         // shortName
        );
        expectedResult.put(1L, row);
        
        when(jobDBLoader.loadMaintenanceRowMap(fromDateTime, toDateTime))
                .thenReturn(expectedResult);

        Map<Long, DbMaintenanceRow> result = jobRepository.getDbMaintenanceRowMap(from, to);

        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(jobDBLoader).loadMaintenanceRowMap(fromDateTime, toDateTime);
    }

    @Test
    void getDbMaintenanceRowMap_ConvertsDatesCorrectly() {
        LocalDate from = LocalDate.of(2024, 6, 20);
        LocalDate to = LocalDate.of(2024, 6, 25);
        LocalDateTime expectedFrom = from.atStartOfDay();
        LocalDateTime expectedTo = to.atStartOfDay();
        
        when(jobDBLoader.loadMaintenanceRowMap(any(), any()))
                .thenReturn(new HashMap<>());

        jobRepository.getDbMaintenanceRowMap(from, to);

        verify(jobDBLoader).loadMaintenanceRowMap(expectedFrom, expectedTo);
    }

@Test
void getFactProductionRowMap_shouldCoverLoadCall() {
    LocalDate from = LocalDate.of(2024, 6, 20);
    LocalDate to = LocalDate.of(2024, 6, 25);

    Map<FactKey, FactProductionRow> returned = new HashMap<>();
    returned.put(new FactKey("X", 1), mock(FactProductionRow.class));

    when(jobDBLoader.loadFactProductionRowMap(any(), any())).thenReturn(returned);

    Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(from, to);

    assertEquals(1, result.size());
    assertTrue(result.containsKey(new FactKey("X", 1)));

    verify(jobDBLoader).loadFactProductionRowMap(from.atStartOfDay(), to.atStartOfDay());
}
}
