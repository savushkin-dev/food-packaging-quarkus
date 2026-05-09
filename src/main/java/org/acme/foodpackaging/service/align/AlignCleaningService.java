package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class AlignCleaningService {

    private static final String PLUSH_TYPE = "10003";

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
            alignLineByStartDateTime(line, factJobs);
        }
    }

    private void alignLineByStartDateTime(Line line, List<Job> factJobs) {
        if (line == null || factJobs.isEmpty() || line.getJobs().isEmpty()) {
            return;
        }

        Job firstPlanned = line.getJobs().getFirst();
        Job firstFact = factJobs.getFirst();

        if (firstFact.getProduct() == null || firstPlanned.getProduct() == null) {
            return;
        }

        if (firstPlanned.getProduct().equals(firstFact.getProduct())) {
            line.setStartDateTime(firstFact.getCameraStart());
        } else {
            line.getJobs().stream()
                    .filter(j ->
                            firstFact.getProduct().equals(j.getProduct())
                                    && j.getStartCleaningDateTime() != null
                                    && j.getStartCleaningDateTime().isBefore(j.getStartProductionDateTime()))
                    .findFirst()
                    .ifPresent(candidate ->
                            applyDelayWithoutFact(candidate, firstFact.getCameraStart()));
        }

        fixLineJobs(line);
        fixPinnedJobs(line);
    }

    private void calculateCleaningDelay(List<Job> jobs, Line line, PackagingSchedule solution) {
        if (jobs.size() < 2 || solution.getDeletedMaintenance() == null) {
            return;
        }

        for (int i = 0; i < jobs.size() - 1; i++) {
            Job curr = jobs.get(i);
            Job next = jobs.get(i + 1);

            if (!areDifferentProducts(curr, next)) {
                continue;
            }

            long cleaningMinutesFact = calculateFactCleaning(curr, next);

            int chainEndIndex = findChainEndIndex(jobs, i + 1);

            if (chainEndIndex > i + 1) {
                List<Job> chain = new ArrayList<>(jobs.subList(i + 1, chainEndIndex));

                handleCleaningDelayForChain(
                        curr, cleaningMinutesFact, chain,
                        line, jobs, solution
                );

                i = chainEndIndex - 2;
            }
        }
    }

    private void handleCleaningDelayForChain(
            Job curr,
            long cleaningMinutesFact,
            List<Job> chain,
            Line line,
            List<Job> jobs,
            PackagingSchedule solution
    ) {
        if (chain.isEmpty()) {
            return;
        }

        chain.sort(Comparator.comparing(
                Job::getStartProductionDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        Job candidate = chain.getFirst();

        if (candidate.getCleaningDelay() != null) {
            return;
        }

        if (candidate.getPreviousJob() != null
                && candidate.getPreviousJob().isMaintenance()) {

            int index = line.getJobs().indexOf(candidate);

            removeMaintenanceBefore(line.getJobs(), index, solution);
            alignLineByStartDateTime(line, jobs);
            applyCleaningDelay(candidate, cleaningMinutesFact);

            return;
        }

        if (isPreviousWithoutFact(candidate)) {
            alignLineByStartDateTime(line, jobs);

            chain.sort(Comparator.comparing(
                    Job::getCameraStart,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ));

            applyDelayWithoutFact(
                    candidate,
                    chain.getFirst().getCameraStart()
            );

            return;
        }

        if (candidate.getStartCleaningDateTime() == null
                || !isPlanProductsValid(curr, candidate)) {
            return;
        }
        applyCleaningDelay(candidate, cleaningMinutesFact);
    }

    private void removeMaintenanceBefore(
            List<Job> jobs,
            int index,
            PackagingSchedule solution
    ) {
        int i = index - 1;

        while (i >= 0) {
            Job job = jobs.get(i);

            if (!job.isMaintenance()) {
                break;
            }

            job.setFDel((short) 1);
            solution.getDeletedMaintenance().add(job);
            jobs.remove(i);

            i--;
        }
        solution.getJobs().removeIf(job -> job.getFDel() == 1);
    }

    boolean isPlanProductsValid(Job curr, Job candidate) {
        return curr.getProduct().equals(
                candidate.getPreviousJob().getProduct()
        );
    }

    private boolean areDifferentProducts(Job curr, Job next) {
        return !curr.getProduct().equals(next.getProduct());
    }

    private boolean isPreviousWithoutFact(Job candidateJob) {
        return Objects.equals(
                candidateJob.getPreviousJob().getProduct().getType(),
                PLUSH_TYPE
        );
    }

    private void applyDelayWithoutFact(
            Job candidate,
            LocalDateTime firstStart
    ) {
        if (firstStart == null
                || candidate.getStartProductionDateTime() == null) {
            return;
        }

        long delay = Duration.between(
                candidate.getStartProductionDateTime(),
                firstStart
        ).toMinutes();

        candidate.setCleaningDelay(Duration.ofMinutes(delay));
    }

    private long calculateFactCleaning(Job curr, Job next) {
        return getCleaningMinutes(
                curr.getCameraEnd(),
                next.getCameraStart()
        );
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

    private void applyCleaningDelay(Job job, long cleaningMinutesFact) {
        long cleaningMinutesPlan = job.getCleaningDurationPlan();

        if (job.areEqualsPlanAndFactLines()) {
            long delay = cleaningMinutesFact - cleaningMinutesPlan;
            job.setCleaningDelay(Duration.ofMinutes(delay));
        }
    }

    private long getCleaningMinutes(
            LocalDateTime start,
            LocalDateTime end
    ) {
        return Duration.between(start, end).toMinutes();
    }

    private List<Job> getFactJobsSorted(List<Job> lineJobs) {
        if (lineJobs == null) {
            return List.of();
        }

        return lineJobs.stream()
                .filter(j ->
                        j.getCameraStart() != null
                                && j.getCameraEnd() != null
                                && j.areEqualsPlanAndFactLines())
                .sorted(Comparator.comparing(Job::getCameraStart))
                .toList();
    }
}

