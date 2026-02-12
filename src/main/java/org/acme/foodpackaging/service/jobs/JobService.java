package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.*;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
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

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

/**
 * Business logic service for job management.
 * Handles job creation and initialization from database rows.
 */
@ApplicationScoped
public class JobService {

    @Inject
    public JobService(LoadDataService loadDataService,
                      UploadDataService uploadDataService, JobRepository jobRepository) {
        this.loadDataService = loadDataService;
        this.uploadDataService = uploadDataService;
        this.jobRepository = jobRepository;
    }
    private  Map<Long, Job> allJobsById;
    private final LoadDataService loadDataService;
    private final UploadDataService uploadDataService;
    private final JobRepository jobRepository;

    /**
     * Инициализирует список задач из базы данных.
     * Фильтрует задачи без lineId и создает Job объекты из DbJobRow и DbMaintenanceRow.
     *
     * @param solution The packaging schedule to initialize
     */
    public Map<Long, DbJobRow> initSolutionJobList(PackagingSchedule solution) {

        MaintenanceData serviceData = jobRepository.getMaintenanceData(
                solution.getWorkCalendar().getFromDate(), solution.getWorkCalendar().getToDate());

        Map<Long, DbJobRow> jobsBySnpz = jobRepository.getDbJobRowMap(
                solution.getWorkCalendar().getFromDate(), solution.getWorkCalendar().getToDate());

        Map<Long, DbMaintenanceRow> maintenanceByFid = serviceData.maintenanceByFid();
        Map<Long, DbMaintenanceRow> cleaningBySnpz = serviceData.cleaningBySnpz();

        List<Job> jobs = new ArrayList<>();
        this.allJobsById = new HashMap<>();
        LocalDateTime minStartDateTime = solution.getWorkCalendar().getMinStartDateTime();

        for (DbJobRow row : jobsBySnpz.values()) {

            Job job = createJobById(row, cleaningBySnpz);
            if(row.lineId()!= null){
                Line line = findLineById(solution, job.getLineId());
                if(line == null) continue;
                if(line.getJobs() == null){
                    line.setJobs(new ArrayList<>());
                }
                job.setMinStartTime(minStartDateTime);
                job.setSaved_job(true);
                job.setLine(line);
                line.getJobs().add(job);
            }
            jobs.add(job);
        }

        for (DbMaintenanceRow rm : maintenanceByFid.values()) {

            Job job = createJobById(rm, solution.getMaintenanceProduct());
            if(rm.getLineId() != null){
                Line line =  findLineById(solution, job.getLineId());
                if(line.getJobs() == null){
                    line.setJobs(new ArrayList<>());
                }
                job.setLine(line);
                job.setMinStartTime(minStartDateTime);
                job.setMaintenance(true);
                job.setSaved_job(true);
                line.getJobs().add(job);
                jobs.add(job);
            }
        }

        solution.setAllJobsById(allJobsById);
        solution.setJobs(jobs);
        return jobsBySnpz;
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
                throw new IllegalArgumentException("Unknown maintenance job FId=" + row.getFId());
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

    private Job createJobById(DbJobRow row, Map<Long, DbMaintenanceRow> cleaningBySnpz){

        if(row == null) return null;
        Job job;
        LocalDateTime cleaningStart = null;
        LocalDateTime startProductionDateTime = getStartProductionDateTime(row.startProductionDateTime());

        Product product = loadDataService.getProducts().get(row.kmc());
        if (product == null) {
            throw new IllegalStateException("Unknown product KMC=" + row.kmc());
        }

        if(row.lineId()!=null) {

            if(cleaningBySnpz.containsKey(row.snpz())){
                cleaningStart = getStartProductionDateTime(cleaningBySnpz.get(row.snpz()).getStartProductionDateTime());
            }
            job = Job.fromDbJobRow(row, product, startProductionDateTime,
                    cleaningStart, ScheduleUtils::nameCleaner);

            if(cleaningStart !=null) {
                long pinned_cleaning = Duration.between(job.getStartProductionDateTime(), job.getStartCleaningDateTime()).toMinutes();
                //job.setPinned_cleaning(true);
                job.setPinned_cleaning_duration((int) pinned_cleaning);
            }
        }
        else{
            job = Job.fromDbJobRow(row, product, null,
                    null, ScheduleUtils::nameCleaner);
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
