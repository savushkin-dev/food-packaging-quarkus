package org.acme.foodpackaging.domain;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @Test
    void setAllFields() {
        LocalDateTime minStartTime = LocalDateTime.of(2025, 1, 15, 8, 0);
        LocalDateTime idealEndTime = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime maxEndTime = LocalDateTime.of(2025, 1, 15, 12, 0);
        LocalDateTime startCleaningDateTime = LocalDateTime.of(2025, 1, 15, 8, 30);
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        
        Product product = new Product("PROD1", "Product 1");
        Duration duration = Duration.ofMinutes(60);
        
        Job.MaintenanceJobTimeParams params = new Job.MaintenanceJobTimeParams(
                "JOB-1",
                "Test Job",
                product,
                duration,
                minStartTime,
                idealEndTime,
                maxEndTime,
                5,
                true,
                startCleaningDateTime,
                startProductionDateTime
        );
        
        Job job = new Job(params);
        
        assertEquals("JOB-1", job.getId());
        assertEquals("Test Job", job.getName());
        assertEquals(product, job.getProduct());
        // getDuration() returns the duration field only for maintenance jobs
        // For non-maintenance jobs, it calculates from speed/quantity
        job.setMaintenance(true);
        assertEquals(duration, job.getDuration());
        assertEquals(minStartTime, job.getMinStartTime());
        assertEquals(idealEndTime, job.getIdealEndTime());
        assertEquals(maxEndTime, job.getMaxEndTime());
        assertEquals(50, job.getPriority()); // priority * 10 = 5 * 10 = 50
        assertTrue(job.isPinned());
        assertEquals(startCleaningDateTime, job.getStartCleaningDateTime());
        assertEquals(startProductionDateTime, job.getStartProductionDateTime());
        assertEquals(startProductionDateTime.plus(duration), job.getEndDateTime());
    }

    @Test
    void zeroPriorityBecomesOne() {
        Product product = new Product("PROD1", "Product 1");
        LocalDateTime startTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        
        Job.MaintenanceJobTimeParams params = new Job.MaintenanceJobTimeParams(
                "JOB-2",
                "Test Job 2",
                product,
                Duration.ofMinutes(30),
                null,
                null,
                null,
                0, // zero priority
                false,
                null,
                startTime
        );
        
        Job job = new Job(params);
        
        assertEquals(1, job.getPriority()); // zero priority should become 1
        assertEquals(startTime.plus(Duration.ofMinutes(30)), job.getEndDateTime());
    }

    @Test
    void nullStartProductionDateTime() {
        Product product = new Product("PROD1", "Product 1");
        
        Job.MaintenanceJobTimeParams params = new Job.MaintenanceJobTimeParams(
                "JOB-3",
                "Test Job 3",
                product,
                Duration.ofMinutes(45),
                null,
                null,
                null,
                2,
                false,
                null,
                null // null startProductionDateTime
        );
        
        Job job = new Job(params);
        
        assertNull(job.getStartProductionDateTime());
        assertNull(job.getEndDateTime()); // endDateTime should be null when startProductionDateTime is null
    }
}
