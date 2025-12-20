package org.acme.foodpackaging.repository.lines;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.lines.LineEntity;
import org.acme.foodpackaging.factory.LineFactory;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.pinnAllLines;

@ApplicationScoped
public class LineRepository  implements PanacheRepository<LineEntity> {

    @Inject
    LineFactory lineFactory;
    @Inject
    LoadDataService loadDataService;
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

    public List<Line> getLines() {

        return loadDataService.getLines().entrySet().stream()
                .map(e -> lineFactory.createLine(
                        e.getKey(),
                        e.getValue()
                ))
                .toList();
    }

    /**
     * Ищет и назначет мимнальное время старта у задач на линии
     */
    public void initJobListOnLine(PackagingSchedule solution) {

        if(solution.getJobs().isEmpty()){
            LocalDateTime startLineDateTime = solution.getWorkCalendar().getMinStartDateTime().plusHours(8);
            for(Line line : solution.getLines()){
                line.setStartDateTime(startLineDateTime);
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
            LocalDateTime maxEndTime = findMaxEndTime(solution.getLines());

            // Проставяет старт всем линиям
            for (Line line : solution.getLines()) {
                initLineStartDateTime(line, maxEndTime);
                fixLineJobs(line);
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
