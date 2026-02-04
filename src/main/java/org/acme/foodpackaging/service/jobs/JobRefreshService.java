package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraEventRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.SelectionValue;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.ProductService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;

@ApplicationScoped
public class JobRefreshService {

    private final JobService jobService;
    private final ProductService productService;
    private final JobRepository jobRepository;
    private final UploadDataService uploadDataService;

    @Inject
    public JobRefreshService(JobService jobService, 
        ProductService productService, JobRepository jobRepository, UploadDataService uploadDataService) {
        this.jobRepository = jobRepository;
        this.uploadDataService = uploadDataService;
        this.jobService = jobService;
        this.productService = productService;
    }

    public PackagingSchedule applySelection(Map<Long, SelectionValue> selection, PackagingSchedule solution) {
        for (Map.Entry<Long, SelectionValue> entry : selection.entrySet()) {
            Long snpz = entry.getKey();
            boolean enabled = entry.getValue().isSelect();
            boolean isHandPackaging = entry.getValue().isLabeling();

            if (enabled) {
                if (!solution.getJobIdMap().containsKey(snpz)) {
                    DbJobRow row = solution.getDbJobRowMap().get(snpz);
                    if (row != null) {
                        Job job = jobService.createJobById(row.snpz(), false, solution);

                        job.setHandPackaging(isHandPackaging);
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

    public void refreshRecentCameraEndIfNeeded(
            PackagingSchedule schedule,
            Map<String, CameraEventRow> endEvents
    ) {
        LocalDateTime now = LocalDateTime.now();
        Duration threshold = Duration.ofHours(12);

        List<Job> candidates = schedule.getJobs().stream()
                .filter(j -> j.getIdBatch() != null)
                .filter(j -> j.getCameraStart() != null)
                .filter(j ->
                        Duration.between(j.getCameraStart(), now)
                                .compareTo(threshold) < 0
                )
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        Set<String> batches = candidates.stream()
                .map(Job::getIdBatch)
                .collect(Collectors.toSet());

        Map<String, LocalDateTime> pmCameraEnds =
                jobRepository. getCameraUpdate(batches);

        for (Job job : candidates) {
            LocalDateTime pmEnd = pmCameraEnds.get(job.getIdBatch());
            if (pmEnd == null) continue;

            CameraEventRow msEndEvent = endEvents.get(job.getIdBatch());
            LocalDateTime msEnd =
                    msEndEvent != null
                            ? msEndEvent.eventTime().toLocalDateTime()
                            : null;

            if (!pmEnd.equals(msEnd)) {
                uploadDataService.updateCameraEndEvent(
                        job.getIdBatch(),
                        pmEnd
                );

                job.setCameraEnd(pmEnd);
            }
        }
    }

}
