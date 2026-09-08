package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.jobs.DelayNoteRequest;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.findLineById;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobNoteService {

    public void writeDelayNote(DelayNoteRequest request, PackagingSchedule solution){
        Line line = findLineById(solution, request.lineId());
        if(line == null || line.getJobs() == null || line.getJobs().isEmpty()) return;

        Job job = line.getJobs().get(request.index());
        job.setDelayNote(request.delayNote());
    }

    public void writeCleaningDelayNote(DelayNoteRequest request, PackagingSchedule solution){
        Line line = findLineById(solution, request.lineId());
        if(line == null || line.getJobs() == null || line.getJobs().isEmpty()) return;

        Job job = line.getJobs().get(request.index());
        job.setCleaningDelayNote(request.delayNote());
    }
}