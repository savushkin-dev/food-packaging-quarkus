package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;

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

        LocalDateTime earliestLineStart = null;
        List<Line> linesWithoutFact = new ArrayList<>();

        for (Line line : solution.getLines()) {

            List<Job> factJobs = getFactJobsSorted(line.getJobs());

            if (factJobs.isEmpty()) {
                linesWithoutFact.add(line);
                continue;
            }

            calculateCleaningDelay(factJobs, line, solution);

            Job firstFactJob = factJobs.getFirst();
            alignLineByStartDateTime(line, firstFactJob);

            LocalDateTime alignedStart = line.getStartDateTime();

            if (earliestLineStart == null || alignedStart.isBefore(earliestLineStart)) {
                earliestLineStart = alignedStart;
            }
        }

        if (earliestLineStart == null) {
            return;
        }

        for (Line line : linesWithoutFact) {
            line.setStartDateTime(earliestLineStart);
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

        long shift = Duration.between(planDateTime, firstFact.getCameraStart()).toMinutes();
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
                        jobs.subList(i + 1, chainEndIndex)
                );

                if (!chain.isEmpty()) {
                    chain.sort(Comparator.comparing(Job::getCameraStart));
                    long cleaningMinutesFact = calculateFactCleaning(curr, chain.getFirst());

                    handleCleaningDelayForChain(
                            curr, cleaningMinutesFact, chain, line, jobs, solution
                    );
                }
                i = chainEndIndex - 1;
            } else {
                i++;
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

        chain.sort(Comparator.comparing(
                Job::getStartProductionDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        Job candidate = chain.getFirst();
        if (candidate.getCleaningDelay() != null) {
            return;
        }

        boolean maintenanceRemoved = tryRemoveMaintenanceBefore(
                candidate, line, solution
        );

        if (maintenanceRemoved) {
            fixLineJobs(line);
            alignLineByStartDateTime(line, jobs.getFirst());
        }


        if (candidate.getPreviousJob() == null) {
            return;
        }

        if (isPreviousWithoutFact(candidate)) {

            alignLineByStartDateTime(line, jobs.getFirst());

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

    private boolean tryRemoveMaintenanceBefore(
            Job candidate,
            Line line,
            PackagingSchedule solution
    ) {
        if (candidate.getPreviousJob() == null
                || !candidate.getPreviousJob().isMaintenance()) {
            return false;
        }

        int index = line.getJobs().indexOf(candidate);
        String mNote = removeMaintenanceBefore(line.getJobs(), index, solution);
        candidate.setCleaningDelayNote(mNote);
        return true;
    }

    private String removeMaintenanceBefore(
            List<Job> jobs,
            int index,
            PackagingSchedule solution
    ) {
        int i = index - 1;
        StringBuilder deletedNotes = new StringBuilder();

        while (i >= 0) {
            Job job = jobs.get(i);

            if (!job.isMaintenance()) {
                break;
            }

            if (job.getMaintenanceNote() != null) {
                if (!deletedNotes.isEmpty()) {
                    deletedNotes.append(", ");
                }
                deletedNotes.append(job.getMaintenanceNote());
            }

            job.setFDel((short) 1);
            solution.getDeletedMaintenance().add(job);
            jobs.remove(i);
            i--;
        }
        solution.getJobs().removeIf(job -> job.getFDel() == 1);
        return deletedNotes.toString();
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
        long delay = cleaningMinutesFact - cleaningMinutesPlan;
        job.setCleaningDelay(Duration.ofMinutes(delay));

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
                .sorted(Comparator.comparing(Job::getCameraEnd))
                .toList();
    }
}

