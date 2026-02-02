package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import java.time.LocalDateTime;

import java.util.Map;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;

@ApplicationScoped
public class JobRefreshService {

    
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final ProductService productService;
    private final UploadDataService uploadDataService;

    @Inject
    public JobRefreshService(JobRepository jobRepository, JobService jobService, 
        ProductService productService, UploadDataService uploadDataService) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.productService = productService;
        this.uploadDataService = uploadDataService;
    }

    public PackagingSchedule applySelection(Map<Long, Boolean> selection, PackagingSchedule solution) {
        for (Map.Entry<Long, Boolean> entry : selection.entrySet()) {
            Long snpz = entry.getKey();
            boolean enabled = entry.getValue();

            if (enabled) {
                if (!solution.getJobIdMap().containsKey(snpz)) {
                    DbJobRow row = solution.getDbJobRowMap().get(snpz);
                    if (row != null) {
                        Job job = jobService.createJobById(row.snpz(), false, solution);

                        solution.getJobs().add(job);
                        solution.getJobIdMap().put(snpz, job);}
                }
            } else {
                Job job = solution.getJobIdMap().remove(snpz);
                if (job != null) {
                    solution.getJobs().remove(job);

                    Line line = job.getLine();
                    if (line != null) {
                        line.getJobs().remove(job);
                        job.setLine(null);

                        fixLineJobs(line);
                        if(line.getFirstUnpinnedIndex()>line.getJobs().size()) {
                            line.setFirstUnpinnedIndex(line.getJobs().size());
                            }
                        }
                    }
                }
            }
        rebuildId(solution);
        solution.setProducts(productService.getProductList(solution));
        return solution;
        }

    private void rebuildId(PackagingSchedule solution) {
        solution.getJobIdMap().clear();
        for (Job j : solution.getJobs()) {
            if(j.isMaintenance()) continue;
           solution.getJobIdMap().put(j.getSnpz(), j);
        }
    }

   /*
    /**
     * Refresh cameraEnd from PM_LOG for each job and update corresponding MS_LOG EVENT=3 DT.
     * Only updates when PM_LOG's DTEND differs from the current job.cameraEnd.
     */

    /*
    public void refreshCameraEnd(PackagingSchedule schedule) {
        for (Job job : schedule.getJobs()) {
            String idBatch = job.getIdBatch();
            if (idBatch == null) continue;
            
            LocalDateTime minFromPmLog = jobRepository.getDtMinByIdBatch(idBatch);
            if (minFromPmLog == null) continue;
            if (!minFromPmLog.equals(job.getCameraEnd())) {
                job.setCameraEnd(minFromPmLog);
                uploadDataService.updateEvent3ForBatch(idBatch, minFromPmLog);
            }
        }
    }*/
}
