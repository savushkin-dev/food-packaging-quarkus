package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.service.lines.LineService;

import java.util.List;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixPinnedJobs;

@ApplicationScoped
public class AlignSolutionService {

    private final AlignDurationService durationService;
    private final AlignCleaningService cleaningService;
    private final LineService lineService;

    @Inject
    public AlignSolutionService(AlignDurationService durationService,
                                AlignCleaningService cleaningService, LineService lineService) {
        this.durationService = durationService;
        this.cleaningService = cleaningService;
        this.lineService = lineService;
    }

    public void align(PackagingSchedule schedule) {
        removeAlignMaintenance(schedule);
        durationService.alignByFactDuration(schedule);
        cleaningService.alignCleanings(schedule);
        lineService.setMaxEndDateTimeByLastJob(schedule);
    }

    public void alignFromScratch(PackagingSchedule schedule){
        reset(schedule);
        align(schedule);
    }

    private void removeAlignMaintenance(PackagingSchedule schedule){
        List<Job> jobs = schedule.getJobs();
        if (schedule.getJobs() == null || schedule.getJobs().isEmpty()) {
            return;
        }

        markForDeleting(schedule);
        jobs.removeIf(job -> job.getFDel() == 1);

        if(schedule.getLines() == null || schedule.getLines().isEmpty()) return;
        for(Line line : schedule.getLines()){

            if (line == null || line.getJobs() == null || line.getJobs().isEmpty()) {
                continue;
            }
            List<Job> lineJobs = line.getJobs();

            lineJobs.removeIf(job -> job.getFDel() == 1);
            fixLineJobs(line);
            fixPinnedJobs(line);
        }
    }

    private void markForDeleting(PackagingSchedule schedule) {
        List<Job> deletedMaintenance = schedule.getDeletedMaintenance();

        for (Job job : schedule.getJobs()) {
            if (job.isMaintenance() && job.getMaintenanceTypeId() != null
                    && (job.getMaintenanceTypeId() == 7
                    || job.getMaintenanceTypeId() == 8 ||
                    job.getMaintenanceTypeId() == 2)
            ) {
                if (job.getMaintenanceTypeId() == 2
                        && job.getPreviousJob() != null
                        && job.getPreviousJob().isMaintenance()
                        && job.getPreviousJob().getMaintenanceTypeId() != null
                        && job.getPreviousJob().getMaintenanceTypeId() != 8) {
                    continue;
                }
                job.setFDel((short) 1);
                deletedMaintenance.add(job);
            }
        }

        schedule.setDeletedMaintenance(deletedMaintenance);
    }

    public void reset(PackagingSchedule solution){
        if(solution == null || solution.getJobs() == null) return;
        for(Job job : solution.getJobs()){
            job.setDrawCleaningStart(null);
            job.setDrawCleaningEnd(null);
            job.setCleaningDelay(null);
            job.setDelayDuration(null);
        }
    }
}
