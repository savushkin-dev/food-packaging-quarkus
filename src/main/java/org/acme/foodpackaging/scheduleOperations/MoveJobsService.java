package org.acme.foodpackaging.scheduleOperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MoveJobsRequest;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import java.util.*;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class MoveJobsService {
    @Inject
    LoadDataService loadDataService;
    /**
     * Выполняет перемещение подпоследовательности задач.
     * Бросает IllegalArgumentException при некорректных входных данных.
     */
    public PackagingSchedule moveJobs(PackagingSchedule schedule, MoveJobsRequest request) {
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Line fromLine = findLineById(schedule, request.getFromLineId());
        Line toLine = findLineById(schedule, request.getToLineId());

        boolean sameLine = fromLine.getId().equals(toLine.getId());

        int fromIndex = request.getFromIndex();
        int count = Math.max(0, request.getCount());
        List<Job> fromJobs = fromLine.getJobs();
        if (fromJobs == null) fromJobs = Collections.emptyList();

        int fromEnd = Math.min(fromIndex + count, fromJobs.size());

        if (fromIndex < 0 || fromIndex >= fromJobs.size() || fromIndex >= fromEnd) {
            throw new IllegalArgumentException("Nothing to move: invalid fromIndex/count");
        }

        if (!sameLine) {
            for (int i = fromIndex; i < fromEnd; i++) {
                Job job = fromJobs.get(i);
                if (job.isMaintenance()) continue;
                String productType = job.getProduct().getType();
                Integer duration = SpeedCacheUtils.getLineSpeeds()
                        .getOrDefault(toLine.getId(), Map.of())
                        .get(productType);
                if (duration == null || duration == 0) {
                    throw new IllegalArgumentException(
                            String.format("Cannot move job \"%s\" to line \"%s\": product type unsupported",
                                    job.getName(), toLine.getName()));
                }
            }
        }

        int insertIndex = request.getInsertIndex();

        List<Job> moved = moveSubList(fromLine, fromIndex, count, toLine, insertIndex);

        if (moved.isEmpty()) {
            return schedule;
        }

        fixLineJobs(fromLine);
        fixPinnedJobs(fromLine);

        if (!sameLine) {
            fixLineJobs(toLine);
            fixPinnedJobs(toLine);
        }

        return schedule;
    }
    /**
     * Перемещает подпоследовательность задач между линиями или внутри одной линии.
     *
     * Поведение зависит от того, совпадают ли линии:
     *
     * 1) Перемещение внутри одной линии:
     *    - Работа ведётся с одним списком задач
     *    - Подсписок [fromIndex, fromIndex + count) удаляется
     *    - Затем он вставляется в позицию insertIndex
     *    - Индексы рассчитываются в одном и том же списке
     *
     * 2) Перемещение между разными линиями:
     *    - Подсписок удаляется из списка исходной линии
     *    - Затем вставляется в список целевой линии
     *
     * Метод работает на копиях списков, чтобы избежать побочных эффектов,
     * и в конце устанавливает обновлённые списки обратно в объекты Line.
     *
     * @param fromLine линия, из которой перемещаются задачи
     * @param fromIndex индекс первой задачи для перемещения
     * @param count количество задач для перемещения
     * @param toLine линия, в которую выполняется вставка
     * @param insertIndex индекс позиции вставки в целевой линии
     *
     * @return список перемещённых задач;
     *         пустой список, если входные параметры некорректны
     */
    private List<Job> moveSubList(Line fromLine, int fromIndex, int count,
                                  Line toLine, int insertIndex) {

        boolean sameLine = fromLine.getId().equals(toLine.getId());

        int fromEnd = Math.min(fromIndex + Math.max(0, count),
                fromLine.getJobs().size());

        if (fromIndex < 0 || fromIndex >= fromLine.getJobs().size() || fromIndex >= fromEnd) {
            return Collections.emptyList();
        }
        // =======================
        // SAME LINE
        // =======================
        if (sameLine) {
            List<Job> jobs = new ArrayList<>(fromLine.getJobs());

            List<Job> moved = new ArrayList<>(jobs.subList(fromIndex, fromEnd));
            jobs.subList(fromIndex, fromEnd).clear();

            insertIndex = Math.max(0, Math.min(insertIndex, jobs.size()));
            jobs.addAll(insertIndex, moved);

            fromLine.setJobs(jobs);
            return moved;
        }
        // =======================
        // ЛОГИКА ДЛЯ РАЗНЫХ ЛИНИЙ
        // =======================
        List<Job> fromJobs = new ArrayList<>(fromLine.getJobs());
        List<Job> toJobs = new ArrayList<>(Optional.ofNullable(toLine.getJobs())
                .orElse(Collections.emptyList()));

        List<Job> jobsToMove = new ArrayList<>(fromJobs.subList(fromIndex, fromEnd));
        fromJobs.subList(fromIndex, fromEnd).clear();

        insertIndex = Math.max(0, Math.min(insertIndex, toJobs.size()));
        toJobs.addAll(insertIndex, jobsToMove);

        fromLine.setJobs(fromJobs);
        toLine.setJobs(toJobs);

        return jobsToMove;
    }
}

