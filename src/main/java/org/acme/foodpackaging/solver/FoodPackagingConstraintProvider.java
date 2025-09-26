package org.acme.foodpackaging.solver;

import java.time.Duration;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.*;

import org.acme.foodpackaging.domain.Job;

public class FoodPackagingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // Hard constraints
                maxEndDateTime(factory),
                enforceNpOrder(factory),
                forbidZeroSpeedProducts(factory),
                // Soft constraints
                minimizeMakespan(factory),
                minimizeCleaningDuration(factory),
        };
    }

    // ************************************************************************
    // Hard constraints
    // ************************************************************************

    protected Constraint maxEndDateTime(ConstraintFactory factory) {
        return factory.forEach(Job.class)
                .filter(job -> job.getEndDateTime() != null && job.getMaxEndTime().isBefore(job.getEndDateTime()))
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                        job -> Duration.between(job.getMaxEndTime(), job.getEndDateTime()).toMinutes())
                .asConstraint("Max end date time");
    }

   protected Constraint forbidZeroSpeedProducts(ConstraintFactory factory) {
        return factory.forEach(Job.class)
                .filter(job -> job.getLine() != null && job.getProduct().getType() != null)
                .filter(job -> {
                    Integer speed = job.getSpeed();
                    return speed != null && speed == 0;
                })
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, job -> 1000L)
                .asConstraint("Forbid products with zero speed on line");
    }
    // ************************************************************************
    // Medium constraints
    // ************************************************************************

    protected Constraint idealEndDateTime(ConstraintFactory factory) {
        return factory.forEach(Job.class)
                .filter(job -> job.getEndDateTime() != null && job.getIdealEndTime().isBefore(job.getEndDateTime()))
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM,
                        job -> Duration.between(job.getIdealEndTime(), job.getEndDateTime()).toMinutes())
                .asConstraint("Ideal end date time");
    }
    // ************************************************************************
    // Soft constraints
    // ************************************************************************
    protected Constraint minimizeMakespan(ConstraintFactory factory) {
        return factory.forEach(Job.class)
                .filter(job -> job.getLine() != null && job.getNextJob() == null)
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, job -> {
                    long minutes = Duration.between(job.getLine().getStartDateTime(), job.getEndDateTime()).toMinutes();
                    return minutes * minutes;
                })
                .asConstraint("Minimize make span");
    }
    // TODO Currently dwarfed by minimizeAndLoadBalanceMakeSpan in the same score level, because that squares
    protected Constraint minimizeCleaningDuration(ConstraintFactory factory) {
        return factory.forEach(Job.class)
                .filter(job -> job.getStartProductionDateTime() != null)
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, job -> 5 * job.getPriority()
                        * Duration.between(job.getStartCleaningDateTime(), job.getStartProductionDateTime()).toMinutes())
                .asConstraint("Minimize cleaning duration");
    }

    Constraint enforceNpOrder(ConstraintFactory factory) {
        return factory.forEachUniquePair(Job.class,
                        Joiners.equal(Job::getLine),
                        Joiners.equal(Job::getProduct))
                .filter((j1, j2) ->
                        j1.getNpAsInt() < j2.getNpAsInt() &&
                                j1.getStartProductionDateTime().isAfter(j2.getStartProductionDateTime()))
                .penalize(HardMediumSoftLongScore.ONE_HARD)
                .asConstraint("Batches must be in increasing order");
    }

}
