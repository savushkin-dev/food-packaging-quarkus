package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.pinnAllLines;

@ApplicationScoped
public class LineService {

    @Inject
    LoadDataService loadDataService;
   
    public List<Line> getLines() {
        return loadDataService.getLines().entrySet().stream()
                .sorted(lineNameComparator())
                .map(e -> new Line(e.getKey(), e.getValue()))
                .toList();
    }

    private Comparator<Map.Entry<String, String>> lineNameComparator() {
        return Comparator
                .comparingInt((Map.Entry<String, String> e) -> extractLineNumber(e.getValue()))
                .thenComparing(Map.Entry::getValue);
    }

    private int extractLineNumber(String name) {
        Matcher matcher = Pattern.compile("№\\s*(\\d+)").matcher(name);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Integer.MAX_VALUE;
    }

    public void initLineStartEnd(PackagingSchedule solution) {

        if(solution.getJobs().isEmpty()){
            LocalDateTime startLineDateTime = solution.getWorkCalendar().getMinStartDateTime().plusHours(8);
            for(Line line : solution.getLines()){
                line.setStartDateTime(startLineDateTime);
                line.setMaxEndTime(startLineDateTime.plusDays(1).toLocalDate().atStartOfDay().plusHours(3));
            }
        }
        else {
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

    public void setMaxEndDateTimeByLastJob(PackagingSchedule solution){

        if(solution.getLines() == null) return;
        for(Line line : solution.getLines()){
            if(line.getJobs().isEmpty()){
                line.setMaxEndTime(line.getStartDateTime().plusHours(20));
            }
            line.setMaxEndTime(line.getJobs().getLast().getEndDateTime().plusHours(20));
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

