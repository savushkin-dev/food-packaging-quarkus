package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.PmLogInsertRow;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.record.CameraEventRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.persistence.upload.UploadDataService;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;

import org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils;

/**
 * Business logic service for job management.
 * Handles job creation and initialization from database rows.
 */
@ApplicationScoped
public class JobService {

    private final LoadDataService loadDataService;
    private final JobRepository jobRepository;
    private final UploadDataService uploadDataService;

    @Inject
    public JobService(
            LoadDataService loadDataService,
            JobRepository jobRepository,
            UploadDataService uploadDataService
    ) {
        this.loadDataService = Objects.requireNonNull(loadDataService, "loadDataService must not be null");
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        this.uploadDataService = Objects.requireNonNull(uploadDataService, "uploadDataService must not be null");
    }

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

            Map<Integer, String> maintenanceTypes =
                    Objects.requireNonNullElse(loadDataService.getMaintenanceTypes(), Collections.emptyMap());
            String maintenanceTypeName =
                    maintenanceTypes.getOrDefault(safe(row.getMaintenanceTypeId()), "Обслуживание");

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
            job.setEventType(factRow.eventType());
            job.setLineIdFact(factRow.lineIdFact());
            job.setDtv(factRow.dtv().toLocalDateTime());
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
     * Initialize camera start/end from MS_LOG events (2=start, 3=end). Falls back to PM_LOG min/max if missing.
     */
    public void initCameraFromEvents(
            PackagingSchedule solution,
            Map<String, CameraEventRow> cameraStartEvents,
            Map<String, CameraEventRow> cameraEndEvents
    ) {
        for (Job job : solution.getJobs()) {
            String idBatch = job.getIdBatch();
            if (idBatch == null) {
                continue;
            }
            applyCameraEventTimes(job, cameraStartEvents.get(idBatch), cameraEndEvents.get(idBatch));
            if (job.getCameraStart() == null || job.getCameraEnd() == null) {
                applyFallbackFromPmLog(job, idBatch);
            }
        }
    }

    private void applyCameraEventTimes(Job job, CameraEventRow startEv, CameraEventRow endEv) {
        if (startEv != null && startEv.eventTime() != null) {
            job.setCameraStart(startEv.eventTime().toLocalDateTime());
        }
        if (endEv != null && endEv.eventTime() != null) {
            job.setCameraEnd(endEv.eventTime().toLocalDateTime());
        }
    }

    private void applyFallbackFromPmLog(Job job, String idBatch) {
        CameraValue fallback = jobRepository.getCameraValueByBatch(idBatch);
        if (fallback == null) {
            return;
        }
        if (job.getCameraStart() == null) {
            job.setCameraStart(fallback.cameraStart());
        }
        if (job.getCameraEnd() == null) {
            job.setCameraEnd(fallback.cameraEnd());
        }
    }
    /**
     * Load-all algorithm: initialize facts and camera using a single MS_LOG payload,
     * then fallback to PM_LOG for missing camera values, and persist missing events.
     */
    public void initFromMsLogEvents(PackagingSchedule schedule, List<FactProductionRow> events) {
        Map<FactKey, FactProductionRow> factMap = buildFactMap(events);
        Map<String, CameraEventRow> startEvents = buildStartEvents(events);
        Map<String, CameraEventRow> endEvents = buildEndEvents(events);
        initFactProductionData(schedule, factMap);
        initCameraFromEvents(schedule, startEvents, endEvents);
        persistMissingCameraEvents(schedule, startEvents, endEvents);
    }

    private Map<FactKey, FactProductionRow> buildFactMap(List<FactProductionRow> events) {
        Map<FactKey, FactProductionRow> factMap = new HashMap<>();
        for (FactProductionRow ev : events) {
            if (ev.eventType() != null && ev.eventType() == 1) {
                FactKey key = new FactKey(ev.kmc(), ev.np());
                factMap.putIfAbsent(key, ev);
            }
        }
        return factMap;
    }

    private Map<String, CameraEventRow> buildStartEvents(List<FactProductionRow> events) {
        Map<String, CameraEventRow> startEvents = new java.util.HashMap<>();
        for (FactProductionRow ev : events) {
            if (ev.eventType() != null && ev.eventType() == 2) {
                CameraEventRow candidate = new CameraEventRow(ev.idBatch(), 2, ev.dtv());
                startEvents.merge(
                        ev.idBatch(),
                        candidate,
                        (oldV, newV) -> oldV.eventTime().before(newV.eventTime()) ? oldV : newV
                );
            }
        }
        return startEvents;
    }

    private Map<String, CameraEventRow> buildEndEvents(List<FactProductionRow> events) {
        Map<String, CameraEventRow> endEvents = new HashMap<>();
        for (FactProductionRow ev : events) {
            if (ev.eventType() != null && ev.eventType() == 3) {
                CameraEventRow candidate = new CameraEventRow(ev.idBatch(), 3, ev.dtv());
                endEvents.merge(
                        ev.idBatch(),
                        candidate,
                        (oldV, newV) -> oldV.eventTime().after(newV.eventTime()) ? oldV : newV
                );
            }
        }
        return endEvents;
    }

    /**
     * Persist camera start/end events (2/3) to MS_LOG for jobs that were initialized
     * from fallback data and don't have corresponding events yet.
     */
    public void persistMissingCameraEvents(
            PackagingSchedule schedule,
            Map<String, CameraEventRow> startEvents,
            Map<String, CameraEventRow> endEvents
    ) {
        Map<String, PmLogInsertRow> toInsertStart = new HashMap<>();
        Map<String, PmLogInsertRow> toInsertEnd = new HashMap<>();
       
        for (Job job : schedule.getJobs()) {
            String idBatch = job.getIdBatch();
            if (idBatch == null) continue;
            String productId = (job.getProduct() != null) ? job.getProduct().getId() : null;
            Integer np = job.getNp();
            LocalDateTime dtv = job.getDtv();
            String lineId = job.getLineIdFact();

            if (!startEvents.containsKey(idBatch) && job.getCameraStart() != null) {
                toInsertStart.put(idBatch, new PmLogInsertRow(
                        idBatch, productId, dtv, np, 2, job.getCameraStart(), lineId
                ));
            }
            if (!endEvents.containsKey(idBatch) && job.getCameraEnd() != null) {
                toInsertEnd.put(idBatch, new PmLogInsertRow(
                        idBatch, productId, dtv, np, 3, job.getCameraEnd(), lineId
                ));
            }
        }
        if (!toInsertStart.isEmpty() || !toInsertEnd.isEmpty()) {
            uploadDataService.writeCameraEventsBatchRows(toInsertStart, toInsertEnd);
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
