package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MsLogInsertRow;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.record.SelectionValue;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.ProductService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.END_CAMERA_EVENT_TYPE;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;

@ApplicationScoped
public class JobRefreshService {

    @Inject
    public JobRefreshService(JobRepository jobRepository, ProductService productService, UploadDataService uploadDataService) {
        this.jobRepository = jobRepository;
        this.productService = productService;
        this.uploadDataService = uploadDataService;
    }

    private final JobRepository jobRepository;
    private final ProductService productService;
    private final UploadDataService uploadDataService;

    public PackagingSchedule applySelection(Map<Long, SelectionValue> selection, PackagingSchedule solution) {
        selection.forEach((snpz, value) -> {
            if (Boolean.TRUE.equals(value.isSelect())) {
                addJobIfAbsent(snpz, Boolean.TRUE.equals(value.isLabeling()), solution);
            } else {
                removeJobFromSolution(snpz, solution);
            }
        });
        solution.setProducts(productService.getProductList(solution));
        return solution;
    }

    private void addJobIfAbsent(Long snpz, boolean isHandPackaging, PackagingSchedule solution) {

        Job job = solution.getAllJobsById().get(snpz);

        if (job == null) {
            return;
        }
    
        if (solution.getJobs().contains(job)) {
            return;
        }
    
        job.setHandPackaging(isHandPackaging);
        job.setMinStartTime(solution.getWorkCalendar().getMinStartDateTime());

        solution.getOverloadedIds().add(snpz.toString());
        solution.getJobs().add(job);
    }

    private void removeJobFromSolution(Long snpz, PackagingSchedule solution) {

        Job job = solution.getAllJobsById().get(snpz);
    
        if (job == null) {
            return;
        }
    
        if (!solution.getJobs().contains(job)) {
            return;
        }
    
        solution.getJobs().remove(job);
    
        Line line = job.getLine();
        if (line != null) {
            line.getJobs().remove(job);
            job.setLine(null);
    
            fixLineJobs(line);
    
            if (line.getFirstUnpinnedIndex() > line.getJobs().size()) {
                line.setFirstUnpinnedIndex(line.getJobs().size());
            }
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
