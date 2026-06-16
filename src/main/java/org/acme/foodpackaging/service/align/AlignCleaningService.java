package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class AlignCleaningService {

    private static final String PLUSH_TYPE = "10003";
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    public void alignCleanings(PackagingSchedule solution) {
        if (solution == null || solution.getLines() == null) {
            return;
        }

        for (Line line : solution.getLines()) {
            List<Job> factJobs = getFactJobsSorted(line.getJobs());

            if (factJobs.isEmpty()) {
                continue;
            }

            calculateCleaningDelay(factJobs, line, solution);
            Job firstFactJob = factJobs.getFirst();
            alignLineByStartDateTime(line, firstFactJob);
        }
    }

    private void alignLineByStartDateTime(Line line, Job firstFact) {

        LocalDateTime planDateTime = firstFact.getStartProductionDateTime();

        Product factProduct = firstFact.getProduct();
        Job previous = firstFact.getPreviousJob();

        while (previous != null && previous.getProduct() != null && previous.getProduct().equals(factProduct)) {
            planDateTime = previous.getStartProductionDateTime();
            previous = previous.getPreviousJob();
        }

        long shift = Duration.between(
                planDateTime.atZone(ZONE_ID),
                firstFact.getCameraStart().atZone(ZONE_ID)).toMinutes();
        if (line.getStartDateTime() != null) {
            line.setStartDateTime(line.getStartDateTime().plusMinutes(shift));
        }
        fixLineJobs(line);
        fixPinnedJobs(line);
    }

    private void calculateCleaningDelay(List<Job> jobs, Line line, PackagingSchedule solution) {
        if (jobs.size() < 2 || solution.getDeletedMaintenance() == null) {
            return;
        }

        int i = 0;
        while (i < jobs.size() - 1) {

            Job curr = jobs.get(i);
            Job next = jobs.get(i + 1);

            if (!areDifferentProducts(curr, next)) {
                i++;
                continue;
            }

            int chainEndIndex = findChainEndIndex(jobs, i + 1);
            if (chainEndIndex > i + 1) {

                List<Job> chain = new ArrayList<>(
                        jobs.subList(i + 1, chainEndIndex));

                if (!chain.isEmpty()) {
                    chain.sort(Comparator.comparing(Job::getCameraStart));
                    long cleaningMinutesFact = calculateFactCleaning(curr, chain.getFirst());

                    handleCleaningDelayForChain(
                            curr, next.getCameraStart(), cleaningMinutesFact, chain, line, jobs);
                }
                i = chainEndIndex - 1;

            } else {
                i++;
            }
        }
    }

    private void handleCleaningDelayForChain(
            Job curr, LocalDateTime nextStart,
            long cleaningMinutesFact,
            List<Job> chain,
            Line line,
            List<Job> jobs) {

        chain.sort(Comparator.comparing(
                Job::getStartProductionDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        Job candidate = chain.getFirst();
        if (candidate.getCleaningDelay() != null) {
            return;
        }

        if (candidate.getPreviousJob() == null) {
            return;
        }

        if (isPreviousWithoutFact(candidate)) {

            alignLineByStartDateTime(line, jobs.getFirst());
            chain.sort(Comparator.comparing(
                    Job::getCameraStart,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            applyDelayWithoutFact(
                    candidate,
                    chain.getFirst().getCameraStart());
            return;
        }

        if (candidate.getStartCleaningDateTime() == null
                || !isPlanProductsValid(curr, candidate)) {
            return;
        }
        applyCleaningDelay(candidate, curr.getCameraEnd(), nextStart, cleaningMinutesFact);
    }

    boolean isPlanProductsValid(Job curr, Job candidate) {
        return curr.getProduct().equals(
                candidate.getPreviousJob().getProduct());
    }

    private boolean areDifferentProducts(Job curr, Job next) {
        return !curr.getProduct().equals(next.getProduct());
    }

    private boolean isPreviousWithoutFact(Job candidateJob) {
        return Objects.equals(
                candidateJob.getPreviousJob().getProduct().getType(),
                PLUSH_TYPE);
    }

    private void applyDelayWithoutFact(
            Job candidate,
            LocalDateTime firstStart) {

        if (firstStart == null
                || candidate.getStartProductionDateTime() == null) {
            return;
        }

        long delay = Duration.between(
                candidate.getStartProductionDateTime().atZone(ZONE_ID),
                firstStart.atZone(ZONE_ID)).toMinutes();

        candidate.setCleaningDelay(Duration.ofMinutes(delay));
    }

    private long calculateFactCleaning(Job curr, Job next) {
        return getCleaningMinutes(
                curr.getCameraEnd(),
                next.getCameraStart());
    }

    private int findChainEndIndex(List<Job> jobs, int startIndex) {
        Job startJob = jobs.get(startIndex);

        int k = startIndex;
        while (k < jobs.size()
                && jobs.get(k).getProduct().equals(startJob.getProduct())) {
            k++;
        }

        return k;
    }

    private void applyCleaningDelay(Job job, LocalDateTime drawStart, LocalDateTime drawEnd, long cleaningMinutesFact) {
        long cleaningMinutesPlan = job.getCleaningDurationPlan();
        long delay = cleaningMinutesFact - cleaningMinutesPlan;
        job.setDrawCleaningStart(drawStart);
        job.setDrawCleaningEnd(drawEnd);
        job.setCleaningDelay(Duration.ofMinutes(delay));

    }

    private long getCleaningMinutes(
            LocalDateTime start,
            LocalDateTime end) {

        if (start == null || end == null) {
            return 0;
        }

        return Duration.between(
                start.atZone(ZONE_ID),
                end.atZone(ZONE_ID)).toMinutes();
    }

    private List<Job> getFactJobsSorted(List<Job> lineJobs) {
        if (lineJobs == null) {
            return List.of();
        }

        return lineJobs.stream()
                .filter(j -> j.getCameraStart() != null
                        && j.getCameraEnd() != null
                        && j.areEqualsPlanAndFactLines())
                .sorted(Comparator.comparing(Job::getCameraEnd))
                .toList();
    }
}
