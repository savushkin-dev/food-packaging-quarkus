package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.Duration;
import java.util.*;

@ApplicationScoped
public class AlignCleaningService {

    public void alignCleanings(PackagingSchedule solution) {
        Map<String, Long> cleaningGaps = collectCleaningGaps(solution);
        applyCleaningDelays(solution, cleaningGaps);
    }

    private void applyCleaningDelays(PackagingSchedule solution,
                                     Map<String, Long> cleaningGaps) {

        for (Job job : solution.getJobs()) {

            Long gapMinutes = cleaningGaps.get(job.getId());
            if (gapMinutes == null || nullDataCleaning(job) || !hasCleaning(job)) {
                continue;
            }

            long actualDelay = Duration.between(
                    job.getStartCleaningDateTime(),
                    job.getStartProductionDateTime()
            ).toMinutes();

            long finalDelay = gapMinutes - actualDelay;

            if (finalDelay > 0) {
                job.setDelayCleaningDuration(Duration.ofMinutes(finalDelay));
            }
        }
    }

    private Map<String, Long> collectCleaningGaps(PackagingSchedule solution) {
        Map<String, Long> result = new HashMap<>();

        for (Line line : solution.getLines()) {
            List<Job> factJobs = getFactJobsSorted(line.getJobs());

            if (factJobs.isEmpty()) {
                continue;
            }

            extractCleaningGaps(factJobs, result);
        }

        return result;
    }

    private void extractCleaningGaps(List<Job> jobs, Map<String, Long> map) {
        for (int i = 1; i < jobs.size(); i++) {
            Job prev = jobs.get(i - 1);
            Job curr = jobs.get(i);

            if (!prev.getProduct().getId().equals(curr.getProduct().getId())) {

                long gapMinutes = Duration.between(
                        prev.getCameraEnd(),
                        curr.getCameraStart()
                ).toMinutes();

                if (gapMinutes > 0) {
                    map.put(curr.getId(), gapMinutes);
                }
            }
        }
    }

    private List<Job> getFactJobsSorted(List<Job> lineJobs) {
        if (lineJobs == null) return List.of();

        return lineJobs.stream()
                .filter(j -> j.getCameraStart() != null && j.getCameraEnd() != null)
                .sorted(Comparator.comparing(Job::getCameraStart)).toList();
    }

    private boolean nullDataCleaning(Job job){
        return job.getStartProductionDateTime() == null ||
                job.getStartCleaningDateTime() == null;
    }

    private boolean hasCleaning(Job job) {
        return job.getStartProductionDateTime()
                .isAfter(job.getStartCleaningDateTime());
    }
}

