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

    @Inject
    LoadDataService loadDataService;

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

            FactKey key = new FactKey(
                    job.getProduct().getId(),
                    job.getNp()
            );

            FactProductionRow factRow = factMap.get(key);

            if (factRow == null) {
                // факт не найден
                continue;
            }

            job.setIdBatch(factRow.idBatch());
            job.setLineIdFact(factRow.lineIdFact());
            job.setStartProductionDateTimeFact(
                    factRow.startProductionDateTimeFact().toLocalDateTime()
            );
        }
    }

    /**
     * Инициализирует фактические данные по камере (начало/конец) по ID партии.
     *
     * @param solution The packaging schedule to initialize
     * @param cameraMap Map keyed by idBatch with camera start/end values
     */
    public void initCameraFactData(
            PackagingSchedule solution,
            Map<String, CameraValue> cameraMap
    ) {
        for (Job job : solution.getJobs()) {
            String idBatch = job.getIdBatch();
            CameraValue camera = (idBatch != null) ? cameraMap.get(idBatch) : null;
            if (camera != null) {
                job.setCameraStart(camera.cameraStart());
                job.setCameraEnd(camera.cameraEnd());
            }
        }
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
