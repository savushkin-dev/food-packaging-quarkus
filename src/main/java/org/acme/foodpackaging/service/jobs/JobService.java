package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.record.MsLogInsertRow;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.repository.jobs.JobRepository;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils;

/**
 * Business logic service for job management.
 * Handles job creation and initialization from database rows.
 */
@ApplicationScoped
public class JobService {

    private static final int START_FACT_EVENT_TYPE = 1;
    private static final int START_CAMERA_EVENT_TYPE = 2;
    private static final int END_CAMERA_EVENT_TYPE = 3;

    @Inject
    LoadDataService loadDataService;

    @Inject
    UploadDataService uploadDataService;

    @Inject
    JobRepository jobRepository;

    /**
     * Инициализирует список задач из базы данных.
     * Фильтрует задачи без lineId и создает Job объекты из DbJobRow и DbMaintenanceRow.
     * 
     * @param solution The packaging schedule to initialize
     */
    public void initSolutionJobList(PackagingSchedule solution) {
        List<Job> jobs = new ArrayList<>();

        for (DbJobRow r : solution.getDbJobRowMap().values()) {
            if (r.lineId() == null) continue;
            Job job = createJobById(r.snpz(), false, solution);
            jobs.add(job);
        }

        for (DbMaintenanceRow rm : solution.getDbMaintenanceRowMap().values()) {
            if (rm.getLineId() == null) continue;
            Job job = createJobById(rm.getFId(), true, solution);
            jobs.add(job);
        }
        
        solution.setJobs(jobs);
    }

    /**
     * Создает задачу по ID из базы данных.
     * Поддерживает как обычные задачи, так и задачи обслуживания (maintenance).
     * 
     * @param id The job ID (SNPZ for regular jobs, FId for maintenance)
     * @param serviceWork Whether this is a maintenance job
     * @param solution The packaging schedule containing the job data
     * @return Created Job object
     * @throws IllegalArgumentException if job not found
     * @throws IllegalStateException if product not found
     */
    public Job createJobById(long id, boolean serviceWork, PackagingSchedule solution) {
        Job job = new Job();

        if (serviceWork) {
            DbMaintenanceRow row = solution.getDbMaintenanceRowMap().get(id);
            if (row == null) {
                throw new IllegalArgumentException("Unknown maintenance job FId=" + id);
            }

            var maintenanceTypes = loadDataService != null ? loadDataService.getMaintenanceTypes() : null;
            String maintenanceTypeName = maintenanceTypes != null
                    ? maintenanceTypes.getOrDefault(safe(row.getMaintenanceTypeId()), "Обслуживание")
                    : "Обслуживание";

            job = Job.fromDbMaintenanceRow(
                    row,
                    maintenanceTypeName,
                    solution.getMaintenanceProduct(),
                    getStartProductionDateTime(row.getStartProductionDateTime())
            );
        } else {
            Job existing = solution.getJobIdMap().get(id);
            if (existing != null) {
                return existing;
            }

            DbJobRow row = solution.getDbJobRowMap().get(id);
            if (row == null) {
                throw new IllegalArgumentException("Unknown SNPZ=" + id);
            }

            Product product = loadDataService.getProducts().get(row.kmc());
            if (product == null) {
                throw new IllegalStateException("Unknown product KMC=" + row.kmc());
            }

            job = Job.fromDbJobRow(row, product, getStartProductionDateTime(row.startProductionDateTime()), ScheduleUtils::nameCleaner);
            solution.getJobIdMap().put(row.snpz(), job);
        }
        job.setMinStartTime(solution.getWorkCalendar().getMinStartDateTime());
        return job;
    }

    /**
     * Инициализирует фактические данные произвосдтва партий.
     * Ищет задачи по ключу Pair<KMC, NP></KMC,>.
     *
     * @param solution The packaging schedule to initialize
     */
    public void initFactProductionData(
        PackagingSchedule solution,
        Map<FactKey, FactProductionRow> factMap
) {

    for (Job job : solution.getJobs()) {

        if (job.getProduct() == null) {
            continue;
        }

        String kmc = job.getProduct().getId();
        Integer np = job.getNp();

        FactProductionRow startFact = factMap.get(new FactKey(kmc, np, START_FACT_EVENT_TYPE));

        if (startFact != null) {
            job.setIdBatch(startFact.idBatch());
            job.setLineIdFact(startFact.lineIdFact());
            job.setDtv(startFact.dtv().toLocalDateTime());
            job.setStartProductionDateTimeFact(startFact.eventTime().toLocalDateTime());
        }

        FactProductionRow startCamera = factMap.get(new FactKey(kmc, np, START_CAMERA_EVENT_TYPE));
        if (startCamera != null) {
            job.setCameraStart(startCamera.eventTime().toLocalDateTime());
        }

        FactProductionRow endCamera = factMap.get(new FactKey(kmc, np, END_CAMERA_EVENT_TYPE));
        if (endCamera != null) {
            job.setCameraEnd(endCamera.eventTime().toLocalDateTime());
        }
    }
}

    /**
     * Инициализирует фактические данные по камере (начало/конец) по ID партии.
     *
     * @param solution The packaging schedule to initialize
     */
    public void enrichCameraFactsFromPmLog(PackagingSchedule solution) {

        List<Job> jobsWithoutCamera = solution.getJobs().stream()
                .filter(j -> j.getIdBatch() != null)
                .filter(j -> j.getCameraStart() == null || j.getCameraEnd() == null)
                .toList();
    
        if (jobsWithoutCamera.isEmpty()) {
            return;
        }
    
        Map<String, CameraValue> cameraMap = jobRepository.getCameraFactRowMap(jobsWithoutCamera);
    
        List<MsLogInsertRow> msLogRows = new ArrayList<>();
    
        for (Job job : jobsWithoutCamera) {
    
            CameraValue camera = cameraMap.get(job.getIdBatch());
            if (camera == null) {
                continue;
            }
    
            if (camera.cameraStart() != null) {
                job.setCameraStart(camera.cameraStart());
    
                msLogRows.add(buildMsLogRow(
                        job,
                        START_CAMERA_EVENT_TYPE,
                        camera.cameraStart()
                ));
            }
    
            if (camera.cameraEnd() != null) {
                job.setCameraEnd(camera.cameraEnd());
    
                msLogRows.add(buildMsLogRow(
                        job,
                        END_CAMERA_EVENT_TYPE,
                        camera.cameraEnd()
                ));
            }
        }
    
        if (!msLogRows.isEmpty()) {
            uploadDataService.fillMsLogTable(msLogRows);
        }
    }
    
    private MsLogInsertRow buildMsLogRow(Job job, int eventType, LocalDateTime eventTime) {
        return new MsLogInsertRow(
                job.getIdBatch(),
                job.getProduct().getId(),
                job.getLineIdFact(),
                job.getNp(),
                eventType,
                Timestamp.valueOf(job.getDtv()),
                Timestamp.valueOf(eventTime)
                
        );
    }
    /**
     * Преобразует Timestamp в LocalDateTime.
     * 
     * @param startProductionDateTime Timestamp to convert
     * @return LocalDateTime or null if input is null
     */
    public LocalDateTime getStartProductionDateTime(Timestamp startProductionDateTime) {
        return startProductionDateTime != null
                ? startProductionDateTime.toLocalDateTime()
                : null;
    }

    private int safe(Integer v) {
        return v != null ? v : 0;
    }
}
