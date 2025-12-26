package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.pinnAllLines;

@ApplicationScoped
public class LineSchedulingService {
    /**
     * Ищет и назначает минимальное время старта у задач на линии
     */
    public void initJobListOnLine(PackagingSchedule solution) {

        if(solution.getJobs().isEmpty()){
            LocalDateTime startLineDateTime = solution.getWorkCalendar().getMinStartDateTime().plusHours(8);
            for(Line line : solution.getLines()){
                line.setStartDateTime(startLineDateTime);
                line.setMaxEndTime(startLineDateTime.plusDays(1).toLocalDate().atStartOfDay().plusHours(3));
            }
        }
        else {
            Map<String, List<Job>> jobsByLineId = solution.getJobs().stream()
                    .filter(job -> job.getLineId() != null)
                    .collect(Collectors.groupingBy(Job::getLineId));

            for (Line line : solution.getLines()) {
                List<Job> lineJobs = jobsByLineId.getOrDefault(line.getId(), List.of());
                List<Job> mutableJobs = new ArrayList<>(lineJobs);

                line.setJobs(mutableJobs);

                for (Job job : mutableJobs) {
                    job.setLine(line);
                }
            }
            pinnAllLines(solution.getLines());
            //  Найти конец самой длинной линии
            LocalDateTime lineEndTime = findMaxEndTime(solution.getLines());

            // Проставляет старт всем линиям
            for (Line line : solution.getLines()) {

                initLineStartDateTime(line, lineEndTime);

                List<Job> jobs = line.getJobs();
                if (jobs == null || jobs.isEmpty()) {
                    continue;
                }

                jobs.sort(Comparator.comparing(
                        Job::getStartProductionDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));

                fixLineJobs(line);

                Job lastJob = jobs.getLast();
                if (lastJob.getEndDateTime() != null) {
                    line.setMaxEndTime(
                            lastJob.getEndDateTime().plusHours(20)
                    );
                }
            }
        }
    }

    private LocalDateTime findMaxEndTime(List<Line> lines) {

        return lines.stream()
                .map(Line::getJobs)
                .filter(jobs -> jobs != null && !jobs.isEmpty())
                .map(List::getLast) // последний job на линии
                .map(Job::getEndDateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private void initLineStartDateTime(Line line, LocalDateTime fallbackStartTime) {

        // Если jobs есть — берём самое раннее начало
        if (line.getJobs() != null && !line.getJobs().isEmpty()) {

            line.getJobs().stream()
                    .map(Job::getStartProductionDateTime)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo).ifPresent(line::setStartDateTime);

        }
        // Если jobs нет — берём конец самой длинной линии
        else if (fallbackStartTime != null) {
            line.setStartDateTime(fallbackStartTime);
        }
    }
}
