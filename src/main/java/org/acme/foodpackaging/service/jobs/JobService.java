package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;

import java.util.List;
import java.util.Map;

import org.acme.foodpackaging.service.lines.LineService;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

/**
 * Business logic service for job management.
 * Handles job creation and initialization from database rows.
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobService {

    private final JobRepository jobRepository;
    private final JobListAssembler jobListAssembler;
    private final JobEnrichmentService jobEnrichmentService;
    private final JobRefreshService refreshService;
    private final LineService lineService;

    public List<JobRow> buildJobsOnLines(PackagingSchedule schedule) {
        List<JobRow> jobRows = initSolutionJobList(schedule);

        initFactProductionData(schedule, jobRepository.getFactProductionRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate()));

        jobEnrichmentService.enrichCameraFactsFromPmLog(schedule);
        jobEnrichmentService.assignIdBatches(schedule);
        refreshService.refreshStaleCameraEndFromPmLog(schedule);
        lineService.initLineStartEnd(schedule);

        return jobRows;
    }

    private List<JobRow> initSolutionJobList(PackagingSchedule solution) {
        JobListAssembler.JobAssemblyResult result = jobListAssembler.assemble(solution);

        solution.setAllJobsById(result.allJobsById());
        solution.setJobs(result.jobs());

        return result.jobRows();
    }

    private void initFactProductionData(PackagingSchedule solution, Map<FactKey, FactProductionRow> factMap) {
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
                job.setDtv(startFact.dtv());
                job.setStartProductionDateTimeFact(startFact.eventTime());
            }

            FactProductionRow startCamera = factMap.get(new FactKey(kmc, np, START_CAMERA_EVENT_TYPE));
            if (startCamera != null) {
                job.setCameraStart(startCamera.eventTime());
            }

            FactProductionRow endCamera = factMap.get(new FactKey(kmc, np, END_CAMERA_EVENT_TYPE));
            if (endCamera != null) {
                job.setCameraEnd(endCamera.eventTime());
            }
        }
    }
}
