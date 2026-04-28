package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class AlignDurationService {

   public void alignByFactDuration(PackagingSchedule schedule) {
        removePackagingMaintenance(schedule);
        if(schedule.getLines()==null) return;
        for (Line line : schedule.getLines()) {
            List<Job> jobs = line.getJobs();
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }
            fixDurationByFact(line);
        }
    }

    private void removePackagingMaintenance(PackagingSchedule schedule){
        List<Job> jobs = schedule.getJobs();
        if (schedule.getJobs() == null || schedule.getJobs().isEmpty()) {
            return;
        }

        Iterator<Job> it = jobs.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.isMaintenance() && job.getMaintenanceTypeId()!= null
                    && job.getMaintenanceTypeId() == 7) {
                job.setFDel((short)1);
                schedule.getDeletedMaintenance().add(job);
                it.remove();
            }
        }

        for(Line line : schedule.getLines()){

            if (line == null || line.getJobs() == null || line.getJobs().isEmpty()) {
                continue;
            }
            List<Job> lineJobs = line.getJobs();

            lineJobs.removeIf(job -> job.isMaintenance() && job.getMaintenanceTypeId()!= null
                    && job.getMaintenanceTypeId() == 7);
            fixLineJobs(line);
            fixPinnedJobs(line);
        }
    }

    private void  fixDurationByFact(Line line) {

        for (Job job : line.getJobs()) {
            Long factMinutes = calculateFactMinutes(job);
            if (factMinutes == null || job.getLineIdFact() == null ||
                    !job.getLine().getId().equals(job.getLineIdFact())) {
                continue;
            }
            long extraMinutes = getExtraTime(job, line.getJobs());
            factMinutes-=extraMinutes;

            long planMinutes = calculatePlanMinutes(job);
            long diff = factMinutes - planMinutes;
            if(diff > 0){
                job.setDelayDuration(Duration.ofMinutes(diff));
                job.setDuration(Duration.ofMinutes(factMinutes));
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
                .filter(j -> j.getLine().getId().equals(j.getLineIdFact()))
                .toList();

        LocalDateTime cameraStart = job.getCameraStart();
        LocalDateTime cameraEnd = job.getCameraEnd();

        return jobsWithFactData.stream()
                .filter(j -> j != job)
                .filter(j ->
                        j.getCameraStart().isBefore(cameraEnd) &&
                                j.getCameraEnd().isAfter(cameraStart)
                )
                .toList();
    }

    // Расчет времени фасовки других партий, в то время как не заверешна предыдущая
    private long getExtraTime(Job job, List<Job> lineJobs){
        long extraTime = 0;
        List<Job> timeIntersectionsJobs = findTimeIntersections(job, lineJobs);
        if(timeIntersectionsJobs.isEmpty()) return extraTime;

        for(Job intersectionJob : timeIntersectionsJobs){
            Long cameraDuration = calculateFactMinutes(intersectionJob);
            if(cameraDuration == null) continue;
            extraTime+=cameraDuration;
        }
        return extraTime;
    }

    private long calculatePlanMinutes(Job job) {
        if (job.getStartProductionDateTime() == null
                || job.getEndDateTime() == null) {
            return 0;
        }

        return ceilMinutes(Duration.between(
                job.getStartProductionDateTime(),
                job.getPlanEndDateTime()
        ));
    }

    private Long calculateFactMinutes(Job job) {
        if (job.getCameraStart() == null
                || job.getCameraEnd() == null) {
            return null;
        }

        return ceilMinutes(Duration.between(
                job.getCameraStart(),
                job.getCameraEnd()
        ));
    }
}
