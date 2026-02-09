package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DailyCleaningService {

    public void addDailyFullCleaning(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            addDailyFullCleaningForLine(line);
        }
    }

    private void addDailyFullCleaningForLine(Line line) {

        List<Job> jobs = line.getJobs();
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        Integer minutes = getDailyCleaningDurationMinutes(line);
        if (minutes == null || minutes <= 0) {
            return;
        }

        Duration requiredDuration = Duration.ofMinutes(minutes);

        CleaningAnchor anchor = findDailyCleaningAnchor(jobs);
        Job anchorJob = anchor.anchorJob();

        if (anchorJob == null || anchorJob.getEndDateTime() == null) {
            return;
        }

        LocalDateTime dailyCleaningTime =
                anchorJob.getEndDateTime().plusHours(24);

        Job targetJob = findJobCoveringTime(jobs, dailyCleaningTime);
        if (targetJob == null) {
            return;
        }

        applyDailyCleaning(targetJob, requiredDuration);
    }

    // ------------------------------------------------------------------
    //  Core logic methods
    // ------------------------------------------------------------------

    /**
     * Ищет самую длинную мойку:
     * - wash (cleaning -> production)
     * - или цепочку подряд идущих maintenance (любой тип)
     */
    private CleaningAnchor findDailyCleaningAnchor(List<Job> jobs) {

        Job bestAnchor = null;
        Duration bestDuration = Duration.ZERO;

        Duration maintenanceChainDuration = Duration.ZERO;
        Job maintenanceChainLastJob = null;

        for (Job job : jobs) {

            // --- Maintenance chain ---
            if (job.isMaintenance() && job.getDuration() != null) {

                maintenanceChainDuration =
                        maintenanceChainDuration.plus(job.getDuration());
                maintenanceChainLastJob = job;

                if (maintenanceChainDuration.compareTo(bestDuration) >= 0) {
                    bestDuration = maintenanceChainDuration;
                    bestAnchor = maintenanceChainLastJob;
                }
                continue;
            }

            maintenanceChainDuration = Duration.ZERO;
            maintenanceChainLastJob = null;

            Duration wash = calculateWashDuration(job);
            if (wash == null || wash.isZero() || wash.isNegative()) {
                continue;
            }

            if (wash.compareTo(bestDuration) >= 0) {
                bestDuration = wash;
                bestAnchor = job;
            }
        }

        return new CleaningAnchor(bestAnchor, bestDuration);
    }

    private void applyDailyCleaning(Job targetJob, Duration required) {

        if (targetJob.isMaintenance()) {

            Duration current = targetJob.getDuration();
            if (current != null && current.compareTo(required) >= 0) {
                return;
            }

            targetJob.setDuration(required);
            return;
        }

        targetJob.setPinned_cleaning(true);
        targetJob.setPinned_cleaning_duration(
                (int) required.toMinutes()
        );
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private Job findJobCoveringTime(List<Job> jobs, LocalDateTime time) {
        for (Job job : jobs) {
            LocalDateTime start = job.getStartProductionDateTime();
            LocalDateTime end = job.getEndDateTime();

            if (start != null && end != null
                    && !time.isBefore(start)
                    && time.isBefore(end)) {
                return job;
            }
        }
        return null;
    }

    private Duration calculateWashDuration(Job job) {
        LocalDateTime cleaningStart = job.getStartCleaningDateTime();
        LocalDateTime productionStart = job.getStartProductionDateTime();

        if (cleaningStart == null || productionStart == null) {
            return null;
        }
        return Duration.between(cleaningStart, productionStart);
    }

    private Integer getDailyCleaningDurationMinutes(Line line) {
        Map<String, Integer> cleanings = CleaningDurationUtils.getLinesCleaning();
        return cleanings != null ? cleanings.get(line.getId()) : null;
    }

    // ------------------------------------------------------------------

    private record CleaningAnchor(
            Job anchorJob,
            Duration totalDuration
    ) {}
}