package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;
import java.util.Comparator;

@ApplicationScoped
public class AlignDurationService {

    public void alignByFactDuration(PackagingSchedule schedule) {
        if (schedule.getLines() == null) {
            return;
        }

        for (Line line : schedule.getLines()) {
            applyLine(line);
        }
    }

    private void applyLine(Line line) {
        List<Job> jobs = line.getJobs();

        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        for (Job job : jobs) {
            applyJob(line, job);
        }
    }

    private void applyJob(Line line, Job job) {

        if (!isEligible(job)) {
            return;
        }

        long factMinutes = computeFactMinutes(job, line);
        long planMinutes = job.getPlanDuration().toMinutes();

        job.setPlanMinutes(planMinutes);
        job.setFactMinutes(factMinutes);

        long diff = factMinutes - planMinutes;

        if (diff > 0) {
            job.setDelayDuration(Duration.ofMinutes(diff));
            fixLineJobs(line);
            fixPinnedJobs(line);
        }
    }

    private boolean isEligible(Job job) {
        return !(job.getDelayDuration() != null
                && job.getDelayDuration().toMinutes() > 0
                && !job.isNeedUpdateDurationForFact())
                && job.getFactDuration() != 0
                && job.areEqualsPlanAndFactLines();
    }

    private long computeFactMinutes(Job job, Line line) {
        long extra = getExtraTime(job, line.getJobs());
        job.setExtraMinutes(extra);

        return job.getFactDuration() - extra;
    }

    private long getExtraTime(Job parentJob, List<Job> lineJobs) {

    LocalDateTime start = parentJob.getCameraStart();
    LocalDateTime end = parentJob.getCameraEnd();

    List<Event> events = new ArrayList<>();

    for (Job j : lineJobs) {

        if (j == parentJob) continue;
        if (!hasCameraData(j)) continue;
        if (!j.areEqualsPlanAndFactLines()) continue;

        LocalDateTime s = j.getCameraStart().isBefore(start)
                ? start
                : j.getCameraStart();

        LocalDateTime e = j.getCameraEnd().isAfter(end)
                ? end
                : j.getCameraEnd();

        if (s.isBefore(e)) {
            events.add(new Event(s, +1));
            events.add(new Event(e, -1));
        }
    }

    if (events.isEmpty()) return 0;

    events.sort(Comparator
            .comparing(Event::time)
            .thenComparing(Event::type)); // end (-1) перед start (+1)

    long extra = 0;
    int active = 0;

    LocalDateTime lastTime = null;

    for (Event e : events) {

        if (active > 0 && lastTime != null) {
            extra += Duration.between(lastTime, e.time()).toMinutes();
        }

        active += e.type();
        lastTime = e.time();
    }

    return extra;
}

    private long overlapMinutes(Job j,
            LocalDateTime start,
            LocalDateTime end) {

        LocalDateTime s = j.getCameraStart().isBefore(start)
                ? start
                : j.getCameraStart();

        LocalDateTime e = j.getCameraEnd().isAfter(end)
                ? end
                : j.getCameraEnd();

        if (!s.isBefore(e)) {
            return 0;
        }

        return Duration.between(s, e).toMinutes();
    }

    private boolean hasCameraData(Job j) {
        return j.getCameraStart() != null && j.getCameraEnd() != null;
    }

    private record Event(LocalDateTime time, int type) {
    // +1 = start, -1 = end
}

}
