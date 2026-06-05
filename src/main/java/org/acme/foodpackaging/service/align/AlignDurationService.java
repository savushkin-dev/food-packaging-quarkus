package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class AlignDurationService {

    private static final Duration ALLOWED_OVERLAP = Duration.ofMinutes(10);

    public void alignByFactDuration(PackagingSchedule schedule) {
        if (schedule.getLines() == null)
            return;
        for (Line line : schedule.getLines()) {
            List<Job> jobs = line.getJobs();
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }
            fixDurationByFact(line);
        }
    }

    private void fixDurationByFact(Line line) {

        for (Job job : line.getJobs()) {
            if ((job.getDelayDuration() != null)
                    || job.getFactDuration() == 0 || !job.areEqualsPlanAndFactLines())
                continue;

            long factMinutes = job.getFactDuration();
            long extraMinutes = getExtraTime(job, line.getJobs());
            factMinutes -= extraMinutes;

            long planMinutes = job.getPlanDuration().toMinutes();
            long diff = factMinutes - planMinutes;
            if (diff > 0) {
                job.setDelayDuration(Duration.ofMinutes(diff));
                fixLineJobs(line);
                fixPinnedJobs(line);
            }
        }
    }

    // Поиск партий, которые пересекаются по времени на линиях
    private List<Job> findTimeIntersections(Job job, List<Job> lineJobs) {

        List<Job> jobsWithFactData = lineJobs.stream()
                .filter(j -> j.getCameraStart() != null)
                .filter(j -> j.getCameraEnd() != null)
                .filter(Job::areEqualsPlanAndFactLines)
                .toList();

        LocalDateTime cameraStart = job.getCameraStart();
        LocalDateTime cameraEnd = job.getCameraEnd();

        return jobsWithFactData.stream()
                .filter(j -> j != job)
                .filter(j -> {

                    LocalDateTime overlapStart = cameraStart.isAfter(j.getCameraStart())
                            ? cameraStart
                            : j.getCameraStart();

                    LocalDateTime overlapEnd = cameraEnd.isBefore(j.getCameraEnd())
                            ? cameraEnd
                            : j.getCameraEnd();

                    Duration overlap = Duration.between(
                            overlapStart.atZone(ZoneId.systemDefault()),
                            overlapEnd.atZone(ZoneId.systemDefault()));

                    return !overlap.isNegative()
                            && overlap.compareTo(ALLOWED_OVERLAP) > 0;
                })
                .toList();
    }

    // Расчет времени фасовки других партий, в то время как не заверешна предыдущая
    private long getExtraTime(Job job, List<Job> lineJobs) {
        long extraTime = 0;
        List<Job> timeIntersectionsJobs = findTimeIntersections(job, lineJobs);
        if (timeIntersectionsJobs.isEmpty())
            return extraTime;

        for (Job intersectionJob : timeIntersectionsJobs) {
            long cameraDuration = intersectionJob.getFactDuration();
            extraTime += cameraDuration;
        }
        return extraTime;
    }
}
