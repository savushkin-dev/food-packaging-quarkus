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
import java.util.stream.Collectors;

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
    private final JobRefreshService jobRefreshService;

    private final int START_CAMERA_EVENT = 2;
    private final int END_CAMERA_EVENT =3;

    @Inject
    public JobService(
            LoadDataService loadDataService,
            JobRepository jobRepository,
            UploadDataService uploadDataService, JobRefreshService jobRefreshService
    ) {
        this.loadDataService = Objects.requireNonNull(loadDataService, "loadDataService must not be null");
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        this.uploadDataService = Objects.requireNonNull(uploadDataService, "uploadDataService must not be null");
        this.jobRefreshService = Objects.requireNonNull(jobRefreshService, "jobRefreshService must not be null");
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
                    factRow.eventTime().toLocalDateTime()
            );
        }
    }

    /**
     * Инициализирует фактические данные по камере (начало/конец) по ID партии.
     *
     * @param solution The packaging schedule to initialize
     * @param startEvents Map keyed by idBatch with camera end values
     * @param endEvents Map keyed by idBatch with camera end values
     */
    public void initCameraFactData(
            PackagingSchedule solution,
            Map<String, CameraEventRow> startEvents,
            Map<String, CameraEventRow> endEvents
    ) {
        for (Job job : solution.getJobs()) {
            String idBatch = job.getIdBatch();
            CameraEventRow cameraRowStart =  (idBatch != null) ?  startEvents.get(idBatch) : null;
            CameraEventRow cameraRowEnd = (idBatch != null) ? endEvents.get(idBatch) : null;
            LocalDateTime cameraStart = (cameraRowStart != null) ?  cameraRowStart.eventTime().toLocalDateTime() : null;
            LocalDateTime cameraEnd = (cameraRowEnd != null) ?  cameraRowEnd.eventTime().toLocalDateTime() : null;
            if (cameraStart != null) {
                job.setCameraStart(cameraStart);
            }
            if(cameraEnd!=null){
                job.setCameraEnd(cameraEnd);
            }
        }
    }

    /**
     * Initialize camera start/end from MS_LOG events (2=start, 3=end). Falls back to PM_LOG min/max if missing.
     */
    public void initFromMsLogEvents(PackagingSchedule schedule, List<FactProductionRow> events) {

        Map<FactKey, FactProductionRow> factMap = buildFactMap(events);
        initFactProductionData(schedule, factMap);

        Map<String, CameraEventRow> startEvents = buildEvents(events, START_CAMERA_EVENT);
        Map<String, CameraEventRow> endEvents = buildEvents(events, END_CAMERA_EVENT);

        initCameraFactData(schedule, startEvents, endEvents);
        Set<String> batchesToLoad = schedule.getJobs().stream()
                .filter(j -> j.getIdBatch() != null)
                .filter(j -> j.getCameraStart() == null || j.getCameraEnd() == null)
                .map(Job::getIdBatch)
                .collect(Collectors.toSet());

        if (!batchesToLoad.isEmpty()) {
            Map<String, CameraValue> pmValues = jobRepository.getCameraValueMap(batchesToLoad);

            for (Job job : schedule.getJobs()) {
                if (!batchesToLoad.contains(job.getIdBatch())) continue;

                CameraValue pm = pmValues.get(job.getIdBatch());
                if (pm == null) continue;

                if (job.getCameraStart() == null && pm.cameraStart() != null) {
                    job.setCameraStart(pm.cameraStart());
                }
                if (job.getCameraEnd() == null && pm.cameraEnd() != null) {
                    job.setCameraEnd(pm.cameraEnd());
                }
            }
        }
        prepareAndPersistFromPmLog(schedule, startEvents, endEvents, batchesToLoad);
        jobRefreshService.refreshRecentCameraEndIfNeeded(schedule, endEvents);

    }

    /**
     * Build fact map for jobs from MS_LOG events.
     */
    private Map<FactKey, FactProductionRow> buildFactMap(List<FactProductionRow> events) {
        Map<FactKey, FactProductionRow> factMap = new HashMap<>();
        for (FactProductionRow ev : events) {
            int START_FACT_EVENT = 1;
            if (ev.eventType() != null && ev.eventType() == START_FACT_EVENT) {
                FactKey key = new FactKey(ev.kmc(), ev.np());
                factMap.putIfAbsent(key, ev);
            }
        }
        return factMap;
    }

    /**
     * Build camera events map (start/end) from MS_LOG events.
     */
    private Map<String, CameraEventRow> buildEvents(List<FactProductionRow> events, int eventType) {
        Map<String, CameraEventRow> eventsList = new HashMap<>();
        for (FactProductionRow ev : events) {
            if (ev.eventType() != null && ev.eventType() == eventType) {
                CameraEventRow candidate = new CameraEventRow(ev.idBatch(), eventType, ev.eventTime());
                if (eventType == START_CAMERA_EVENT) {
                    eventsList.merge(ev.idBatch(), candidate,
                            (oldV, newV) -> oldV.eventTime().toLocalDateTime().isBefore(newV.eventTime().toLocalDateTime()) ? oldV : newV);
                } else {
                    eventsList.merge(ev.idBatch(), candidate,
                            (oldV, newV) -> oldV.eventTime().toLocalDateTime().isAfter(newV.eventTime().toLocalDateTime()) ? oldV : newV);
                }
            }
        }
        return eventsList;
    }

    /**
     * Prepare camera events that were missing in MS_LOG (filled from PM_LOG)
     * and call persist to insert them.
     */
    private void prepareAndPersistFromPmLog(
            PackagingSchedule schedule,
            Map<String, CameraEventRow> startEvents,
            Map<String, CameraEventRow> endEvents,
            Set<String> batchesLoadedFromPmLog
    ) {
        Map<String, PmLogInsertRow> toInsertStart = new HashMap<>();
        Map<String, PmLogInsertRow> toInsertEnd = new HashMap<>();

        for (Job job : schedule.getJobs()) {
            String idBatch = job.getIdBatch();
            if (idBatch == null || !batchesLoadedFromPmLog.contains(idBatch)) continue;


            String productId = job.getProduct() != null ? job.getProduct().getId() : null;
            Integer np = job.getNp();
            LocalDateTime dtv = job.getDtv();
            String lineId = job.getLineIdFact();

            // Insert only if missing in MS_LOG
            if (!startEvents.containsKey(idBatch) && job.getCameraStart() != null) {
                toInsertStart.put(idBatch, new PmLogInsertRow(
                        idBatch, productId, dtv, np, START_CAMERA_EVENT, job.getCameraStart(), lineId
                ));
            }

            if (!endEvents.containsKey(idBatch) && job.getCameraEnd() != null) {
                toInsertEnd.put(idBatch, new PmLogInsertRow(
                        idBatch, productId, dtv, np, END_CAMERA_EVENT, job.getCameraEnd(), lineId
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
