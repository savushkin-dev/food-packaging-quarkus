package domain;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.createMaintenanceProduct;
import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @Test
    void setMaintenanceFields() {
        LocalDateTime startCleaningDateTime = LocalDateTime.of(2025, 1, 15, 8, 30);
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(60);
        
        Product product =  createMaintenanceProduct();
        Duration duration = Duration.ofMinutes(60);

        DbMaintenanceRow row = new DbMaintenanceRow(
                1L, (short)0, "1600", Timestamp.valueOf(startProductionDateTime),Timestamp.valueOf(endDateTime), 60,2212L, 4, "Note"
                );
        Job job = Job.fromDbMaintenanceRow(row,"123354","Maintenance Name", product, startProductionDateTime);
        
        assertEquals("1", job.getId());
        assertEquals("123354", job.getLineId());
        assertEquals("Maintenance Name", job.getName());
        assertEquals(product, job.getProduct());
        // getDuration() returns the duration field only for maintenance jobs
        // For non-maintenance jobs, it calculates from speed/quantity
        job.setMaintenance(true);
        assertEquals(duration, job.getDuration());
        assertEquals(1, job.getPriority());
        assertTrue(job.isPinned());
        assertEquals(startProductionDateTime, job.getStartProductionDateTime());
        assertEquals(startProductionDateTime.plus(duration), job.getEndDateTime());
    }

    @Test
    void setProductionFields() {
        LocalDateTime dti = LocalDateTime.of(2025, 1, 1, 8, 30);
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(20);

        Product product =  new Product("12", "Vanilla");
        Duration duration = Duration.ofMinutes(20);

        DbJobRow row = new DbJobRow(
                Timestamp.valueOf(dti),"1623", 34,5600,1600.23,
                Timestamp.valueOf(startProductionDateTime),Timestamp.valueOf(endDateTime),
                20,3L, 0, "17000234", "Strawberry");
        Job job = Job.fromDbJobRow(row, product, startProductionDateTime, ScheduleUtils::nameCleaner);

        assertEquals("3", job.getId());
        assertEquals("17000234", job.getLineId());
        assertEquals(product, job.getProduct());
        assertEquals("Strawberry", job.getName());
        assertEquals(34, job.getNp());
        assertEquals(1600.23, job.getMass());
        assertEquals(1, job.getPriority());
        assertEquals(startProductionDateTime, job.getStartProductionDateTime());
        assertEquals(startProductionDateTime.plus(duration), job.getEndDateTime());
    }
}
