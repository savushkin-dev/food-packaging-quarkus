package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixPinnedJobs;

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
            calculateCleaningDelay(factJobs, line);
            alignLineByStartDateTime(line, factJobs);
        }
    }

    private void alignLineByStartDateTime(Line line, List<Job> factJobs){
        if(line.getJobs().getFirst().getProduct().getId().equals(factJobs.getFirst().getProduct().getId())){
            line.setStartDateTime(factJobs.getFirst().getCameraStart());
        }
        fixLineJobs(line);
        fixPinnedJobs(line);
    }

    private void calculateCleaningDelay(List<Job> jobs, Line line) {
        if (jobs == null || jobs.size() < 2) {
            return;
        }

        for (int i = 0; i < jobs.size() - 1; i++) {
            Job curr = jobs.get(i);
            Job next = jobs.get(i + 1);

            if (!isTheSameProduct(curr, next)) {
                continue;
            }

            long cleaningMinutesFact = calculateFactCleaning(curr, next);

            int chainEndIndex = findChainEndIndex(jobs, i + 1);
            List<Job> chain = jobs.subList(i + 1, chainEndIndex);

            Job jobWithCleaning = findJobWithCleaning(chain);

            if (jobWithCleaning == null || chain.isEmpty()) {
                continue;
            }

            if (isPreviousWithoutFact(jobWithCleaning)) {
                alignLineByStartDateTime(line, jobs);
                applyDelayWithoutFact(jobWithCleaning, chain.getFirst().getCameraStart());
            } else {
                applyCleaningDelay(jobWithCleaning, cleaningMinutesFact);
            }
            i = chainEndIndex - 2;
        }
    }

    private boolean isTheSameProduct(Job curr, Job next) {
        if (curr == null || next == null) return false;
        if (curr.getProduct() == null || next.getProduct() == null) return false;

        return !Objects.equals(
                curr.getProduct().getId(),
                next.getProduct().getId()
        );
    }

    private boolean isPreviousWithoutFact(Job candidateJob){
        if (isInvalidJobWithProductType(candidateJob) ||
                isInvalidJobWithProductType(candidateJob.getPreviousJob())) return false;

        final String PLUSH_TYPE = "10003";
        return candidateJob.getPreviousJob().getProduct().getType().equals(PLUSH_TYPE);
    }

    private void applyDelayWithoutFact(Job candidate, LocalDateTime firstStart) {
        if (candidate == null || firstStart == null || candidate.getStartProductionDateTime() == null) {
            return;
        }

        long delay = Duration.between(
                candidate.getStartProductionDateTime(),
                firstStart
        ).toMinutes();

        candidate.setCleaningDelay(Duration.ofMinutes(delay));
    }

    private boolean isInvalidJobWithProductType(Job job) {
        return job == null || job.getProduct() == null
                || job.getProduct().getType() == null;
    }

    private long calculateFactCleaning(Job curr, Job next) {
        return getCleaningMinutes(
                curr.getCameraEnd(),
                next.getCameraStart()
        );
    }

    private int findChainEndIndex(List<Job> jobs, int startIndex) {
        String productId = jobs.get(startIndex).getProduct().getId();

        int k = startIndex;

        while (k < jobs.size() &&
                Objects.equals(jobs.get(k).getProduct().getId(), productId)) {
            k++;
        }
        return k;
    }

    private void applyCleaningDelay(Job job, long cleaningMinutesFact) {
        long cleaningMinutesPlan = getCleaningMinutes(
                job.getStartCleaningDateTime(),
                job.getStartProductionDateTime()
        );

        if (isTheSameLine(job)) {
            long delay = cleaningMinutesFact - cleaningMinutesPlan;
            job.setCleaningDelay(Duration.ofMinutes(delay));
        }
    }

    private boolean isTheSameLine(Job candidate) {
         return candidate.getLine().getId().equals(candidate.getLineIdFact());
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
}

