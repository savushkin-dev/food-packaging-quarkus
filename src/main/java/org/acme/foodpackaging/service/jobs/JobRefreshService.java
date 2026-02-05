package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.record.DbJobRow;
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
    public JobRefreshService(JobRepository jobRepository, JobService jobService, 
        ProductService productService, UploadDataService uploadDataService) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.productService = productService;
        this.uploadDataService = uploadDataService;
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

    /**
     * Обновляет данные по камере в MS_LOG для партий со времени старта которых прошло меньше 12 часов.
     *
     * @param solution The packaging schedule to initialize
     */
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
            if (camera != null && camera.cameraEnd() != null
                    && differsMoreThan(job.getCameraEnd(), camera.cameraEnd())) {
                job.setCameraEnd(camera.cameraEnd());
                msLogRows.add(new MsLogInsertRow(
                        job, END_CAMERA_EVENT_TYPE,
                        Timestamp.valueOf(camera.cameraEnd())
                ));
            }
        }

        if (!msLogRows.isEmpty()) {
            uploadDataService.updateCameraEndInMsLog(msLogRows);
        }
    }
/**
 * Возвращает {@code true}, если значения отличаются не менее чем на одну минуту.
 * @param a предыдущее значение времени по камере
 * @param b новое значение времени по камере из БД
 * @return {@code true}, если значения различаются более чем на одну минуту, иначе {@code false}
*/
    private boolean differsMoreThan(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) {
        return a != b;
        }
    return Math.abs(Duration.between(a, b).toMinutes()) >= 1;
    }
}
