package org.acme.foodpackaging.solver;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.*;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;

public class FoodPackagingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // Hard constraints
                maxEndDateTimePerLine(factory),
                forbidZeroSpeedProducts(factory),
                // Medium constraints
                minimizeCleaningDuration(factory),
                // Soft constraints
                minimizeMakespan(factory),
        };
    }

    // ************************************************************************
    // Hard constraints
    // ************************************************************************
    protected Constraint maxEndDateTimePerLine(ConstraintFactory factory) {
        return factory.forEach(Line.class)
                .filter(line ->
                        line.getMaxEndTime() != null
                                && line.getJobs() != null
                                && !line.getJobs().isEmpty()
                )
                .filter(line -> {
                    LocalDateTime lineEnd =
                            line.getJobs().stream()
                                    .map(Job::getEndDateTime)
                                    .filter(Objects::nonNull)
                                    .max(LocalDateTime::compareTo)
                                    .orElse(null);

                    return lineEnd != null && lineEnd.isAfter(line.getMaxEndTime());
                })
                .penalizeLong(
                        HardMediumSoftLongScore.ONE_HARD,
                        line -> {
                            LocalDateTime lineEnd =
                                    line.getJobs().stream()
                                            .map(Job::getEndDateTime)
                                            .filter(Objects::nonNull)
                                            .max(LocalDateTime::compareTo)
                                            .orElse(line.getMaxEndTime());

                            return Duration
                                    .between(line.getMaxEndTime(), lineEnd)
                                    .toMinutes();
                        }
                )
                .asConstraint("Max end date time per line");
    }

    protected Constraint maxEndDateTime(ConstraintFactory factory) {
        return factory.forEach(Job.class)
                .filter(job -> job.getEndDateTime() != null && job.getMaxEndTime().isBefore(job.getEndDateTime()))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
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
                .filter(job -> job.getStartProductionDateTime() != null
                        && job.getStartCleaningDateTime() != null)
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM,
                        job -> {
                            long minutes = Duration.between(
                                    job.getStartCleaningDateTime(),
                                    job.getStartProductionDateTime()
                            ).toMinutes();

                            long safeMinutes = Math.max(0, minutes);

                            return job.getPriority() * safeMinutes;
                        })
                .asConstraint("Minimize cleaning duration");
    }
}
