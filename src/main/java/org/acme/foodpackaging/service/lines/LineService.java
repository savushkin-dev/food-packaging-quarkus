package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

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

        if (solution.getLines() == null) {
            return;
        }

        LocalDateTime defaultStart =
                solution.getWorkCalendar()
                        .getPlanningDate()
                        .atStartOfDay();

        for (Line line : solution.getLines()) {

            List<Job> jobs = line.getJobs();

            if (jobs == null || jobs.isEmpty()) {
                line.setStartDateTime(defaultStart);
                line.setMaxEndTime(defaultStart.plusHours(24));
                continue;
            }

            jobs.sort(
                    Comparator.comparing(
                            Job::getStartProductionDateTime,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
            );

            Job firstJob = jobs.getFirst();
            Job lastJob = jobs.getLast();

            line.setStartDateTime(firstJob.getStartProductionDateTime());

            fixLineJobs(line);
            fixPinnedJobs(line);

            if (lastJob.getEndDateTime() != null) {
                line.setMaxEndTime(lastJob.getEndDateTime().plusHours(24));
            }
        }
    }

    public void setMaxEndDateTimeByLastJob(PackagingSchedule solution) {

        if (solution.getLines() == null) return;
        for (Line line : solution.getLines()) {

            if(line.getStartDateTime() == null){
                line.setStartDateTime(solution.getWorkCalendar().getPlanningDate().atStartOfDay());
            }

            if (line.getJobs() == null || line.getJobs().isEmpty()) {
                line.setMaxEndTime(line.getStartDateTime().plusHours(24));
                continue;
            }

            line.setMaxEndTime(line.getJobs().getLast().getEndDateTime().plusHours(24));
        }
    }

    public Map<String, Double> calculateLineProductions(List<Line> lines, LocalDate selectedDate) {
        Map<String, Double> lineProductionsMap = LinkedHashMap.newLinkedHashMap(lines.size());
        for (Line line : lines) {
            if (line.getJobs() == null || line.getJobs().isEmpty()) {
                lineProductionsMap.put(line.getName(), 0.0);
                continue;
            }
            List<Job> jobsByDate = line.getJobs().stream()
                    .filter(j -> j.getCameraStart() != null && j.getCameraEnd() != null
                            && j.getCameraStart().toLocalDate().isEqual(selectedDate)
                            && j.getCameraEnd().toLocalDate().isEqual(selectedDate)).toList();
            double lineProduction = 0;

            for(Job j : jobsByDate){
                lineProduction += j.getMass();
            }

            lineProductionsMap.put(line.getName(), lineProduction);
        }
         return lineProductionsMap;
    }
}

