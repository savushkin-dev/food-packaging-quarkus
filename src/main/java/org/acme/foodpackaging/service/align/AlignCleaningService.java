package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class AlignCleaningService {

    public void alignCleanings(PackagingSchedule solution) {
        applyCleaningDelays(solution);
    }

    private void applyCleaningDelays(PackagingSchedule solution) {
        for (Line line : solution.getLines()) {
            List<Job> factJobs = getFactJobsSorted(line.getJobs());

            if (factJobs.isEmpty()) {
                continue;
            }

            calculateCleaningDelay(factJobs);
        }
    }

    private void calculateCleaningDelay(List<Job> jobs) {
        List<Job> chainEqualsProducts = new ArrayList<>();
        for (int i = 0; i < jobs.size() - 1; i++) {
            Job curr = jobs.get(i);
            Job next = jobs.get(i + 1);
            String currProductId = jobs.get(i).getProduct().getId();
            String nextProductId = jobs.get(i).getProduct().getId();

            if (currProductId.equals(nextProductId)) {

                chainEqualsProducts.add(curr);
                chainEqualsProducts.add(next);
            } else {

                long cleaningMinutesFact = getCleaningMinutes(
                        curr.getCameraEnd(), next.getCameraStart());

                Job jobWithCleaning = findJobWithCleaning(chainEqualsProducts);

                if(jobWithCleaning != null) {
                    long cleaningMinutesPlan = getCleaningMinutes(jobWithCleaning.getStartCleaningDateTime(),
                            jobWithCleaning.getStartProductionDateTime());

                    if (isCandidate(jobWithCleaning, cleaningMinutesFact, cleaningMinutesPlan)) {
                        long cleaningDelay = cleaningMinutesFact - cleaningMinutesPlan;
                        jobWithCleaning.setCleaningDelay(Duration.ofMinutes(cleaningDelay));
                    }
                    chainEqualsProducts.clear();
                }
            }
        }
    }

    private boolean isCandidate(Job candidate,
                                long cleaningMinutesFact, long cleaningMinutesPlan) {
        if (!candidate.getLine().getId().equals(candidate.getLineIdFact())
                || isPreviousWithoutFact(candidate))
            return false;

        return cleaningMinutesFact > cleaningMinutesPlan;

    }

    private long getCleaningMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return Duration.between(
                start, end
        ).toMinutes();
    }

    private Job findJobWithCleaning(List<Job> chainEqualsProducts) {
        return chainEqualsProducts.stream()
                .filter(job ->
                        job.getStartProductionDateTime() != null &&
                                job.getStartCleaningDateTime() != null &&
                                job.getStartProductionDateTime().isAfter(job.getStartCleaningDateTime())
                )
                .findFirst()
                .orElse(null);
    }

    private List<Job> getFactJobsSorted(List<Job> lineJobs) {
        if (lineJobs == null) return List.of();

        return lineJobs.stream()
                .filter(j -> j.getCameraStart() != null && j.getCameraEnd() != null
                        && j.getLine().getId().equals(j.getLineIdFact()))
                .sorted(Comparator.comparing(Job::getCameraStart)).toList();
    }

    private boolean isPreviousWithoutFact(Job job) {
        final String PLUSH_TYPE = "10003";
        return job.getProduct().getType().equals(PLUSH_TYPE);
    }
}

