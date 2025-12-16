package org.acme.foodpackaging.repository.lines;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.entity.lines.LineEntity;
import org.acme.foodpackaging.factory.LineFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;

@ApplicationScoped
public class LineRepository  implements PanacheRepository<LineEntity> {

    @Inject
    LineFactory lineFactory;
    /**
     * Загружает id и название линии
     */
    public ConcurrentMap<String, String> loadLines() {
        List<LineEntity> lines = list("fDel = 0");

        return lines.stream()
                .collect(Collectors.toConcurrentMap(
                        e -> e.getKrc().trim(),
                        e -> e.getSnm().trim(),
                        (existing, replacement) -> existing
                ));
    }

    public List<Line> getLines(Map<String, LocalDateTime> lineStartsTime){
        return lineStartsTime.entrySet().stream()
                .map(e -> lineFactory.createLine(e.getKey(), e.getValue()))
                .toList();
    }

    public void initJobListOnLine(List<Line> lines, List<Job> jobs){
        /*
          Группировка jobs по lineId
          заполнение списков jobs у lines
          каждому job проставляется ссылка на line
         */
        Map<String, List<Job>> jobsByLineId = jobs.stream()
                .filter(job -> job.getLineId() != null)
                .collect(Collectors.groupingBy(Job::getLineId));

        for (Line line : lines) {

            List<Job> lineJobs = jobsByLineId.getOrDefault(line.getId(), List.of());
            List<Job> mutableJobs = new ArrayList<>(lineJobs);

            line.setJobs(mutableJobs);

            for (Job job : mutableJobs) {
                job.setLine(line);
            }

            fixLineJobs(line);
        }
    }
}
