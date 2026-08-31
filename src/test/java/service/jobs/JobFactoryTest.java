package service.jobs;

import builder.JobRowBuilder;
import builder.ProductTestBuilder;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.exception.service.ProductNotFoundException;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.jobs.JobFactory;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import builder.MaintenanceRowBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ExtendWith(MockitoExtension.class)
class JobFactoryTest {

    @InjectMocks
    JobFactory jobFactory;

    @Mock
    LoadDataService loadDataService;

    @Test
    void createProductionJob_shouldThrowException_whenProductNotFound() {
        JobRow jobRow = JobRowBuilder.aRow().withKmc("UNKNOWN").build();

        when(loadDataService.getProducts()).thenReturn(Map.of("P1", ProductTestBuilder.aProduct("P1").build()));

        assertThrows(ProductNotFoundException.class,
                () -> jobFactory.createProductionJob(jobRow, new HashMap<>()));
    }

    @Test
    void createProductionJob_returnsNull_whenRowIsNull() {
        Job result = jobFactory.createProductionJob(null, new HashMap<>());

        assertNull(result);
        verifyNoInteractions(loadDataService);
    }

    @Test
    void createProductionJob_registersJobInAllJobsByIdMap() {
        JobRow jobRow = JobRowBuilder.aRow().withSnpz(123L).withKmc("P1").withLineId("L1").build();
        Product product = ProductTestBuilder.aProduct("P1").build();

        when(loadDataService.getProducts()).thenReturn(Map.of("P1", product));

        Map<Long, Job> allJobsById = new HashMap<>();
        Job job = jobFactory.createProductionJob(jobRow, allJobsById);

        assertNotNull(job);
        assertSame(job, allJobsById.get(123L));
    }

    @Test
    void createProductionJob_setsNullStartTime_whenLineIdIsNull() {
        JobRow jobRow = JobRowBuilder.aRow().withSnpz(123L).withKmc("P1").withLineId(null).build();
        Product product = ProductTestBuilder.aProduct("P1").build();

        when(loadDataService.getProducts()).thenReturn(Map.of("P1", product));

        Job job = jobFactory.createProductionJob(jobRow, new HashMap<>());

        assertNull(job.getStartProductionDateTime());
    }

    @Test
    void createMaintenanceJob_shouldThrowException_whenRowIsNull() {
        Product product = ProductTestBuilder.aProduct("P1").build();
        assertThrows(IllegalArgumentException.class,
                () -> jobFactory.createMaintenanceJob(null, product));
    }

    @Test
    void createMaintenanceJob_usesMaintenanceTypeName_whenTypeFound() {
        MaintenanceRow row = MaintenanceRowBuilder.aRow().withEventTypeId(7).build();
        Product maintenanceProduct = ProductTestBuilder.aProduct("MAINT").build();

        when(loadDataService.getMaintenanceTypes())
                .thenReturn(new ConcurrentHashMap<>(Map.of(7, "Мойка")));

        Job job = jobFactory.createMaintenanceJob(row, maintenanceProduct);

        assertEquals("Мойка", job.getName());
    }

    @Test
    void createMaintenanceJob_usesDefaultName_whenTypeNotFound() {
        MaintenanceRow row = MaintenanceRowBuilder.aRow().withEventTypeId(99).build();
        Product maintenanceProduct = ProductTestBuilder.aProduct("MAINT").build();

        when(loadDataService.getMaintenanceTypes()).thenReturn(new ConcurrentHashMap<>(Map.of(2, "Обслуживание")));

        Job job = jobFactory.createMaintenanceJob(row, maintenanceProduct);

        assertEquals("Обслуживание", job.getName());
    }

    @Test
    void createMaintenanceJob_usesDefaultName_whenEventTypeIdIsNull() {
        MaintenanceRow row = MaintenanceRowBuilder.aRow().withEventTypeId(null).build();
        Product maintenanceProduct = ProductTestBuilder.aProduct("MAINT").build();

        when(loadDataService.getMaintenanceTypes()).thenReturn(new ConcurrentHashMap<>(Map.of(0, "Обслуживание")));

        Job job = jobFactory.createMaintenanceJob(row, maintenanceProduct);

        assertEquals("Обслуживание", job.getName());
    }
}