package service.jobs;

import builder.JobRowBuilder;
import builder.ProductTestBuilder;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.exception.service.ProductNotFoundException;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.jobs.JobFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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
}

