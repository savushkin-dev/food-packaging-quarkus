package org.acme.foodpackaging.service.solution;

import jakarta.enterprise.context.ApplicationScoped;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.service.solution.value.DowntimeDataValue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Считает суммарное время простоя по уже посчитанному солверу расписанию
 * (in-memory PackagingSchedule), сразу после solve и после save.
 *
 * Не путать с {@link org.acme.foodpackaging.service.load.BatchDowntimeService},
 * который ищет простои по логам БД для одной конкретной партии.
 */
@ApplicationScoped
public class ScheduleDowntimeService {

    public DowntimeDataValue calculate(PackagingSchedule solution) {
        if (isInvalidSolution(solution)) {
            return new DowntimeDataValue("", 0, Map.of());
        }

        Duration totalDowntime = Duration.ZERO;
        Map<String, Long> lineDownTimes = new LinkedHashMap<>();
        LocalDate planningDate = solution.getWorkCalendar().getPlanningDate();

        for (Line line : solution.getLines()) {
            if (line == null)
                continue;

            Duration lineDowntime = calculateLineDowntime(line, solution.getOverloadedIds());
            totalDowntime = totalDowntime.plus(lineDowntime);

            lineDownTimes.put(line.getId(), lineDowntime.toMinutes());
        }

        return new DowntimeDataValue(planningDate.toString(), totalDowntime.toMinutes(), lineDownTimes);
    }

    private boolean isInvalidSolution(PackagingSchedule solution) {
        return solution == null
                || solution.getLines() == null
                || solution.getOverloadedIds().isEmpty()
                || solution.getWorkCalendar() == null
                || solution.getWorkCalendar().getPlanningDate() == null;
    }

    private Duration calculateLineDowntime(Line line, Set<String> targetIds) {
        if (line.getJobs() == null)
            return Duration.ZERO;

        Duration lineDowntime = Duration.ZERO;

        for (Job job : line.getJobs()) {
            if (job == null || job.getId() == null)
                continue;

            if (targetIds.contains(job.getId())) {
                Duration jobDowntime = calculateJobDowntime(job);
                lineDowntime = lineDowntime.plus(jobDowntime);
            }
        }
        return lineDowntime;
    }

    private Duration calculateJobDowntime(Job job) {
        ZoneId zoneId = ZoneId.systemDefault();
        if (job.getStartProductionDateTime() == null
                || job.getStartCleaningDateTime() == null) {
            return Duration.ZERO;
        }

        Duration diff = Duration.between(
                job.getStartCleaningDateTime().atZone(zoneId),
                job.getStartProductionDateTime().atZone(zoneId));

        return diff.isNegative() ? Duration.ZERO : diff;
    }
}
