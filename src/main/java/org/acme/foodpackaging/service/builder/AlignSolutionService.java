package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class AlignSolutionService {

    @Inject
    MaintenanceJob maintenanceJob;
    public void alignByFactDuration(PackagingSchedule schedule) {

        for (Line line : schedule.getLines()) {

            List<Job> jobs = line.getJobs();
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }

            List<MaintenanceToInsert> toInsert = new ArrayList<>();

            for (Job job : jobs) {

                Long factMinutes = calculateFactMinutes(job);
                if (factMinutes == null) {
                    continue;
                }

                long planMinutes = calculatePlanMinutes(job);

                if (factMinutes > planMinutes) {
                    long diff = factMinutes - planMinutes;
                    toInsert.add(new MaintenanceToInsert(job, diff));
                }
            }

            for (int i = toInsert.size() - 1; i >= 0; i--) {

                MaintenanceToInsert item = toInsert.get(i);

                int index = line.getJobs().indexOf(item.job);

                if (index < 0) {
                    continue;
                }

                MaintenanceRequest request = new MaintenanceRequest();
                request.setLineId(line.getId());
                request.setInsertIndex(index + 1);
                request.setDurationMinutes((int) item.diffMinutes);
                request.setMaintenanceTypeId(7);
                request.setMaintenanceNote(
                        "Отклонение факт > план. Job id=" + item.job.getId()
                );

                maintenanceJob.addMaintenanceJob(schedule, request);
            }
        }
    }

    private long calculatePlanMinutes(Job job) {
        if (job.getStartProductionDateTime() == null
                || job.getEndDateTime() == null) {
            return 0;
        }

        return ceilMinutes(Duration.between(
                job.getStartProductionDateTime(),
                job.getEndDateTime()
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

    private long ceilMinutes(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 0;
        }
        return (duration.toSeconds() + 59) / 60;
    }

    private static class MaintenanceToInsert {
        private final Job job;
        private final long diffMinutes;

        public MaintenanceToInsert(Job job, long diffMinutes) {
            this.job = job;
            this.diffMinutes = diffMinutes;
        }
    }

    public void alignLineStartByFact(PackagingSchedule schedule) {

        for (Line line : schedule.getLines()) {

            List<Job> jobs = line.getJobs();
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }

            List<Job> factJobs = jobs.stream()
                    .filter(j -> j.getCameraStart() != null)
                    .filter(j -> j.getCameraEnd() != null)
                    .filter(j -> j.getStartProductionDateTime() != null)
                    .toList();

            if (factJobs.isEmpty()) {
                continue;
            }


            Job earliestPlanJob = factJobs.stream()
                    .min(Comparator.comparing(Job::getStartProductionDateTime))
                    .orElse(null);

            Job earliestFactJob = factJobs.stream()
                    .min(Comparator.comparing(Job::getCameraStart))
                    .orElse(null);

            LocalDateTime factStart = earliestFactJob.getCameraStart();

            int index = jobs.indexOf(earliestPlanJob);
            if (index < 0) {
                continue;
            }

            Job previous = earliestPlanJob.getPreviousJob();

            LocalDateTime referenceTime;

            if (previous != null && previous.getEndDateTime() != null) {
                referenceTime = previous.getEndDateTime();
            } else {
                referenceTime = earliestPlanJob.getStartProductionDateTime();
            }

            long diffMinutes = ceilMinutes(Duration.between(referenceTime, factStart));

            if (diffMinutes > 0) {

                MaintenanceRequest request = new MaintenanceRequest();
                request.setLineId(line.getId());
                request.setInsertIndex(index);
                request.setDurationMinutes((int) diffMinutes);
                request.setMaintenanceTypeId(8);
                request.setMaintenanceNote(
                        "Сдвиг старта линии по факту. PlanJob id="
                                + earliestPlanJob.getId()
                );

                maintenanceJob.addMaintenanceJob(schedule, request);
            }
        }
    }
}
