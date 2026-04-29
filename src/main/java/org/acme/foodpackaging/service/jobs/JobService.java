package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.DelayNoteRequest;
import org.acme.foodpackaging.exception.service.ProductNotFoundException;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.dto.MsLogInsertRow;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.repository.jobs.JobRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
import org.acme.foodpackaging.service.lines.LineService;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

/**
 * Business logic service for job management.
 * Handles job creation and initialization from database rows.
 */
@ApplicationScoped
public class JobService {

    @Inject
    public JobService(LoadDataService loadDataService, 
        UploadDataService uploadDataService, JobRepository jobRepository, JobInfoService jobInfoService,
                      JobRefreshService refreshService, LineService lineService) {
        this.loadDataService = loadDataService;
        this.uploadDataService = uploadDataService;
        this.jobRepository = jobRepository;
        this.jobInfoService = jobInfoService;
        this.refreshService = refreshService;
        this.lineService = lineService;
    }

    private final LoadDataService loadDataService;
    private final UploadDataService uploadDataService;
    private final JobRepository jobRepository;
    private final JobInfoService jobInfoService;
    private final JobRefreshService refreshService;
    private final LineService lineService;
    private  Map<Long, Job> allJobsById;

    public List<DbJobRow> buildJobsOnLines(PackagingSchedule schedule){
        List<DbJobRow> jobRows = initSolutionJobList(schedule);
        initFactProductionData(schedule, jobRepository.getFactProductionRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );

        enrichCameraFactsFromPmLog(schedule);
        initIdBatch(schedule);
        refreshService.refreshStaleCameraEndFromPmLog(schedule);
        lineService.initLineStartEnd(schedule);
        return jobRows;
    }
    /**
     * Инициализирует список задач из базы данных.
     * Фильтрует задачи без lineId и создает Job объекты из DbJobRow и DbMaintenanceRow.
     * 
     * @param solution The packaging schedule to initialize
     */
    public List<DbJobRow> initSolutionJobList(PackagingSchedule solution) {

        List<DbMaintenanceRow> serviceData = jobRepository.getMaintenanceData(
                solution.getWorkCalendar().getFromDate(), solution.getWorkCalendar().getToDate());

        Map<Long, DbMaintenanceRow> delayDurationMap  = jobRepository.getDelayData(
                solution.getWorkCalendar().getFromDate(), solution.getWorkCalendar().getToDate());

        Map<Long, DbMaintenanceRow> cleaningDelayDurationMap  = jobRepository.getCleaningDelayData(
                solution.getWorkCalendar().getFromDate(), solution.getWorkCalendar().getToDate());

        Map<Long,DbJobRow> jobsBySnpz = jobRepository.getDbJobRowMap(
                solution.getWorkCalendar().getFromDate(), solution.getWorkCalendar().getToDate());

        List<Job> jobs = new ArrayList<>();
        this.allJobsById = new HashMap<>();
        LocalDateTime minStartDateTime = solution.getWorkCalendar().getMinStartDateTime();

        for (DbJobRow row : jobsBySnpz.values()) {

            Job job = createJobById(row);
            if(row.lineId()!= null){
                Line line = findLineById(solution, job.getLineId());
                if(line == null) continue;
                if(line.getJobs() == null){
                    line.setJobs(new ArrayList<>());
                }
                job.setDti(row.dti().toLocalDateTime());
                job.setMinStartTime(minStartDateTime);
                job.setLine(line);
                line.getJobs().add(job);
                jobs.add(job);
            }

        }

        for (DbMaintenanceRow rm : serviceData) {

            Job job = createJobById(rm, solution.getMaintenanceProduct());
            if(rm.getLineId() != null){
                Line line =  findLineById(solution, job.getLineId());
                if(line.getJobs() == null){
                    line.setJobs(new ArrayList<>());
                }
                job.setLine(line);
                job.setMinStartTime(minStartDateTime);
                job.setMaintenance(true);
                line.getJobs().add(job);
                jobs.add(job);
            }
        }

        solution.setAllJobsById(allJobsById);
        solution.setJobs(jobs);
        initDelayDuration(solution.getJobs(), delayDurationMap);
        initCleaningDelayDuration(solution.getJobs(), cleaningDelayDurationMap);
        return jobsBySnpz.values().stream().toList();
    }

    private void initDelayDuration(List<Job> jobs, Map<Long, DbMaintenanceRow> delayDurationMap){
        for(Job job : jobs){
            long jobId;
            try {
                jobId = Long.parseLong(job.getId());
            } catch (NumberFormatException e) {
                continue;
            }
            if(delayDurationMap.containsKey(jobId)){
                DbMaintenanceRow row = delayDurationMap.get(jobId);
                job.setEndDateTime(row.getEndDateTime().toLocalDateTime());
                job.setDelayDuration(Duration.ofMinutes(row.getDuration()));
                job.setDelayNote(row.getMaintenanceNote());
            }
        }
    }

    private void initCleaningDelayDuration(List<Job> jobs, Map<Long, DbMaintenanceRow> cleaningDelayDurationMap){
        for(Job job : jobs){
            long jobId;
            try {
                jobId = Long.parseLong(job.getId());
            } catch (NumberFormatException e) {
                continue;
            }
            if(cleaningDelayDurationMap.containsKey(jobId)){
                DbMaintenanceRow row = cleaningDelayDurationMap.get(jobId);
                job.setCleaningDelay(Duration.ofMinutes(row.getDuration()));
                job.setCleaningDelayNote(row.getMaintenanceNote());
            }
        }
    }

    /**
     * Создает задачу по ID из базы данных.
     * Поддерживает как обычные задачи, так и задачи обслуживания (maintenance).
     *
     * @param row data from db about serviceWork
     * @param maintenanceProduct common product for all serviceWork
     * @return Created Job object
     * @throws IllegalArgumentException if maintenance not found
     * @throws IllegalStateException if product not found
     */
    private Job createJobById(DbMaintenanceRow row, Product maintenanceProduct) {
        Job job;

        if (row == null) {
            throw new IllegalArgumentException("Unknown maintenance job: row is null");
        }

        var maintenanceTypes = loadDataService != null ? loadDataService.getMaintenanceTypes() : null;
        String maintenanceTypeName = maintenanceTypes != null
                ? maintenanceTypes.getOrDefault(safe(row.getMaintenanceTypeId()), "Обслуживание")
                : "Обслуживание";

        job = Job.fromDbMaintenanceRow(
                row,
                maintenanceTypeName,
                maintenanceProduct,
                getStartProductionDateTime(row.getStartProductionDateTime())
        );
        return job;
    }

    private Job createJobById(DbJobRow row){
        if(row == null) return null;

        Product product = loadDataService.getProducts().get(row.kmc());
            if (product == null) {
                throw new ProductNotFoundException(row.kmc());
            }

        Job job = null;
        if(row.lineId()!=null) {
            job = Job.fromDbJobRow(row, product, row.startProductionDateTime().toLocalDateTime(),
                    ScheduleUtils::nameCleaner);
        }
        else{
            job = Job.fromDbJobRow(row, product, null,
                    ScheduleUtils::nameCleaner);
        }

        allJobsById.put(row.snpz(),job);
        return job;
    }

    /**
     * Инициализирует фактические данные произвосдтва партий.
     * Ищет задачи по ключу record FactKey{KMC, NP, EVENT_TYPE}.
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
     * Инициализирует  недостающие данные по камере (начало/конец) по ID партии.
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
    
            if (job.getCameraStart()== null && camera.cameraStart() != null) {
                job.setCameraStart(camera.cameraStart());
    
                msLogRows.add(new MsLogInsertRow(
                        job, START_CAMERA_EVENT_TYPE,
                        Timestamp.valueOf(job.getCameraStart())
                ));
            }
    
            if (job.getCameraEnd()== null && camera.cameraEnd() != null) {
                job.setCameraEnd(camera.cameraEnd());
    
                msLogRows.add(new MsLogInsertRow(
                        job, END_CAMERA_EVENT_TYPE,
                        Timestamp.valueOf(job.getCameraEnd())
                ));
            }
        }
    
        if (!msLogRows.isEmpty()) {
            uploadDataService.fillMsLogTable(msLogRows);
        }
    }

    public void writeDelayNote(DelayNoteRequest request, PackagingSchedule solution){
        Line line = findLineById(solution, request.getLineId());
        if(line == null || line.getJobs() == null || line.getJobs().isEmpty()) return;

        Job job = line.getJobs().get(request.getIndex());
        job.setDelayNote(request.getDelayNote());
    }

     public void writeCleaningDelayNote(DelayNoteRequest request, PackagingSchedule solution){
        Line line = findLineById(solution, request.getLineId());
        if(line == null || line.getJobs() == null || line.getJobs().isEmpty()) return;

        Job job = line.getJobs().get(request.getIndex());
        job.setCleaningDelayNote(request.getDelayNote());
    }

    public void initIdBatch(PackagingSchedule schedule){
        for(Job job : schedule.getJobs()){
            if( job.isMaintenance() || job.getIdBatch() != null) continue;
            try {
                long jobIdAsLong = Long.parseLong(job.getId());
                job.setIdBatch(jobInfoService.generateIdBatch(schedule, jobIdAsLong));
            } catch (NumberFormatException e) {
                // Skip this job if its ID cannot be parsed to a long
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
