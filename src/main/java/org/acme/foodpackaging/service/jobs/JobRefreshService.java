package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.MsLogInsertRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.ProductService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.END_CAMERA_EVENT_TYPE;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;

@ApplicationScoped
public class JobRefreshService {

    @Inject
    JobRepository jobRepository;
    @Inject
    JobService jobService;
    @Inject
    ProductService productService;
    @Inject
    UploadDataService uploadDataService;

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
           solution.getJobIdMap().put(j.getSnpz(), j);
        }
    }

    public void refreshStaleCameraEndFromPmLog(PackagingSchedule solution) {

        LocalDateTime threshold = LocalDateTime.now().minusHours(12);

        List<Job> staleCameraJobs = solution.getJobs().stream()
                .filter(j -> j.getIdBatch() != null)
                .filter(j -> j.getCameraStart() != null)
                .filter(j -> j.getCameraStart().isAfter(threshold))
                .toList();

        if (staleCameraJobs.isEmpty()) {
            return;
        }

        Map<String, CameraValue> cameraMap =
                jobRepository.getCameraFactRowMap(staleCameraJobs);

        List<MsLogInsertRow> msLogRows = new ArrayList<>();

        for (Job job : staleCameraJobs) {

            CameraValue camera = cameraMap.get(job.getIdBatch());
            if (camera == null || camera.cameraEnd() == null) {
                continue;
            }

            LocalDateTime oldEnd = job.getCameraEnd();
            LocalDateTime newEnd = camera.cameraEnd();

            if (!differsMoreThan(oldEnd, newEnd)) {
                continue;
            }

            job.setCameraEnd(camera.cameraEnd());

            MsLogInsertRow row = new MsLogInsertRow(
                    job, END_CAMERA_EVENT_TYPE,
                    Timestamp.valueOf(camera.cameraEnd())
            );

            msLogRows.add(row);
        }

        if (!msLogRows.isEmpty()) {
            uploadDataService.updateCameraEndInMsLog(msLogRows);
        }
    }

    private boolean differsMoreThan(LocalDateTime a, LocalDateTime b) {
        return Math.abs(Duration.between(a, b).toMinutes()) >= 1;
    }
}
