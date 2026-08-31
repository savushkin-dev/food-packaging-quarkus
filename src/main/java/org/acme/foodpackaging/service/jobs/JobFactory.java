package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.exception.service.ProductNotFoundException;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;

import java.time.LocalDateTime;
import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobFactory {

    private static final String DEFAULT_MAINTENANCE_NAME = "Обслуживание";

    private final LoadDataService loadDataService;

    public Job createProductionJob(JobRow row, Map<Long, Job> allJobsById) {
        if (row == null) {
            return null;
        }
        Product product = loadDataService.getProducts().get(row.kmc());
        if (product == null) {
            throw new ProductNotFoundException(row.kmc());
        }

        LocalDateTime startTime = row.lineId() != null ? row.startProductionDateTime() : null;
        Job job = Job.fromJobRow(row, product, startTime, ScheduleUtils::nameCleaner);

        allJobsById.put(row.snpz(), job);
        return job;
    }

    public Job createMaintenanceJob(MaintenanceRow row, Product maintenanceProduct) {
        if (row == null) {
            throw new IllegalArgumentException("Unknown maintenance job: row is null");
        }
        String typeName = loadDataService.getMaintenanceTypes()
                .getOrDefault(safe(row.eventTypeId()), DEFAULT_MAINTENANCE_NAME);

        return new Job(row, typeName, maintenanceProduct);
    }

    private int safe(Integer v) {
        return v != null ? v : 0;
    }
}
