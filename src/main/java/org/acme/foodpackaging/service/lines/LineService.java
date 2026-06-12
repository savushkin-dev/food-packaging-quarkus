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
                line.setMaxEndTime(defaultStart.plusHours(20));
                continue;
            }

            jobs.sort(
                    Comparator.comparing(
                            Job::getStartProductionDateTime,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
            );

            fixLineJobs(line);
            fixPinnedJobs(line);

            Job firstJob = jobs.getFirst();
            Job lastJob = jobs.getLast();

            line.setStartDateTime(firstJob.getStartProductionDateTime());

            if (lastJob.getEndDateTime() != null) {
                line.setMaxEndTime(lastJob.getEndDateTime().plusHours(20));
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
                line.setMaxEndTime(line.getStartDateTime().plusHours(20));
                continue;
            }

            line.setMaxEndTime(line.getJobs().getLast().getEndDateTime().plusHours(20));
        }
    }
}

