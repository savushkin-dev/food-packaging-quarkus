package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.row.jobs.MsLogInsertRow;
import org.acme.foodpackaging.exception.service.CameraDataReadException;
import org.acme.foodpackaging.service.upload.UploadDataService;
import org.acme.foodpackaging.dto.row.jobs.CameraFactRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.domain.value.FactKey.EventType.END_CAMERA;
import static org.acme.foodpackaging.domain.value.FactKey.EventType.START_CAMERA;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobEnrichmentService {

    private final JobRepository jobRepository;
    private final UploadDataService uploadDataService;
    private final JobInfoService jobInfoService;

    /**
     * Инициализирует недостающие данные по камере (начало/конец) по ID партии.
     */
    public void enrichCameraFactsFromPmLog(PackagingSchedule solution) {

        List<Job> jobsWithoutCamera = solution.getJobs().stream()
                .filter(j -> j.getCameraStart() == null || j.getCameraEnd() == null)
                .toList();

        if (jobsWithoutCamera.isEmpty()) {
            return;
        }

        Map<String, CameraFactRow> cameraMap;
        try {
            cameraMap = jobRepository.getCameraFactRowMap(jobsWithoutCamera);
        } catch (CameraDataReadException e) {
            throw new RuntimeException("Failed to read camera data", e);
        }

        List<MsLogInsertRow> msLogRows = new ArrayList<>();

        for (Job job : jobsWithoutCamera) {

            CameraFactRow camera = job.getIdBatch() == null ? null : cameraMap.get(job.getIdBatch());
            if (camera == null) {
                continue;
            }

            if (job.getCameraStart() == null && camera.cameraStart() != null) {
                job.setCameraStart(camera.cameraStart());
                msLogRows.add(new MsLogInsertRow(job, START_CAMERA.code(), job.getCameraStart()));
            }

            if (job.getCameraEnd() == null && camera.cameraEnd() != null) {
                job.setCameraEnd(camera.cameraEnd());
                msLogRows.add(new MsLogInsertRow(job, END_CAMERA.code(), job.getCameraEnd()));
            }
        }

        if (!msLogRows.isEmpty()) {
            uploadDataService.fillMsLogTable(msLogRows);
        }
    }

    /**
     * Генерирует idBatch для задач, у которых он ещё не проставлен.
     * Пропускает задачи обслуживания и задачи с уже существующим idBatch.
     */
    public void assignIdBatches(PackagingSchedule schedule) {
        for (Job job : schedule.getJobs()) {
            if (job.isMaintenance() || job.getIdBatch() != null) {
                continue;
            }
            try {
                long jobIdAsLong = Long.parseLong(job.getId());
                job.setIdBatch(jobInfoService.generateIdBatch(schedule, jobIdAsLong));
            } catch (NumberFormatException e) {
                // job id не числовой — idBatch не может быть сгенерирован, пропускаем
            }
        }
    }
}