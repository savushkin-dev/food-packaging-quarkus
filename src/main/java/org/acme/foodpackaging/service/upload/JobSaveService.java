package org.acme.foodpackaging.service.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobSaveService {

    private final MaintenanceSaveService maintenanceSaveService;
    private final ProductionJobSaveService productionJobSaveService;
    private final DrawCleaningSaveService drawCleaningSaveService;
    private final ParallelOperationSaveService parallelOperationSaveService;

    @Transactional
    public void saveJobsByType(PackagingSchedule schedule) {

        maintenanceSaveService.markDeleted(schedule);

        for (Job job : schedule.getJobs()) {

            if (job.isMaintenance()) {
                maintenanceSaveService.save(job);
                continue;
            }

            productionJobSaveService.save(job);
            drawCleaningSaveService.save(job);
        }

        parallelOperationSaveService.markDeleted(schedule);
        parallelOperationSaveService.save(schedule);
    }
}
