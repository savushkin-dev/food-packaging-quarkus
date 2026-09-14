package org.acme.foodpackaging.service.scheduleoperations;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.request.jobs.MoveJobsRequest;
import org.acme.foodpackaging.utils.SpeedCacheUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.*;

import static org.acme.foodpackaging.utils.ScheduleUtils.findLineById;
import static org.acme.foodpackaging.utils.ScheduleUtils.fixPinnedJobs;
import static org.acme.foodpackaging.utils.ScheduleUtils.fixLineJobs;

@ApplicationScoped
public class MoveJobsService {

    /**
     * Выполняет перемещение подпоследовательности задач.
     * Бросает IllegalArgumentException при некорректных входных данных.
     */
    public void moveJobs(PackagingSchedule schedule, MoveJobsRequest request) {
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Line fromLine = findLineById(schedule, request.fromLineId());
        Line toLine = findLineById(schedule, request.toLineId());

        if (fromLine == null) {
            throw new IllegalArgumentException("Line not found: " + request.fromLineId());
        }
        if (toLine == null) {
            throw new IllegalArgumentException("Line not found: " + request.toLineId());
        }

        boolean sameLine = fromLine.getId().equals(toLine.getId());

        int fromIndex = request.fromIndex();
        int count = request.count();
        List<Job> fromJobs = fromLine.getJobs();
        if (fromJobs == null) fromJobs = Collections.emptyList();

        if (fromIndex < 0 || count <= 0 || fromIndex >= fromJobs.size()) {
            throw new IllegalArgumentException("Nothing to move: invalid fromIndex/count");
        }
        int fromEnd = (int) Math.min((long) fromIndex + (long) count, fromJobs.size());

        if (!sameLine) {
            validateProductTypesSupported(fromJobs, fromIndex, fromEnd, toLine);
        }

        int insertIndex = request.insertIndex();

        List<Job> moved = moveSubList(fromLine, fromIndex, count, toLine, insertIndex);

        if (moved.isEmpty()) {
            return;
        }

        fixLineJobs(fromLine);
        fixPinnedJobs(fromLine);

        if (!sameLine) {
            fixLineJobs(toLine);
            fixPinnedJobs(toLine);
        }

    }
    /**
     * Проверяет, что каждая перемещаемая (не ремонтная) задача поддерживается на целевой линии.
     * Бросает IllegalArgumentException, если тип продукта задачи не поддерживается на целевой линии.
     */
    private void validateProductTypesSupported(List<Job> fromJobs, int fromIndex, int fromEnd, Line toLine) {
        for (int i = fromIndex; i < fromEnd; i++) {
            Job job = fromJobs.get(i);
            if (job.isMaintenance()) continue;

            String productType = job.getProduct().getType();
            Integer speed = SpeedCacheUtils.getSpeed(toLine.getId(), productType);
            if (speed == null || speed == 0) {
                throw new IllegalArgumentException(
                        String.format("Cannot move job \"%s\" to line \"%s\": product type unsupported",
                                job.getName(), toLine.getName()));
            }
        }
    }

    /**
     * Перемещает подпоследовательность задач между линиями или внутри одной линии.
     * <p>
     * Поведение зависит от того, совпадают ли линии:
     * <p>
     * 1) Перемещение внутри одной линии:
     *    - Работа ведётся с одним списком задач
     *    - Подсписок [fromIndex, fromIndex + count) удаляется
     *    - Затем он вставляется в позицию insertIndex
     *    - Индексы рассчитываются в одном и том же списке
     * <p>
     * 2) Перемещение между разными линиями:
     *    - Подсписок удаляется из списка исходной линии
     *    - Затем вставляется в список целевой линии
     * <p>
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

        int fromSize = fromLine.getJobs().size();
        if (fromIndex < 0 || count <= 0 || fromIndex >= fromSize) {
            return Collections.emptyList();
        }

        int fromEnd = (int) Math.min((long) fromIndex + (long) count, fromSize);
        // =======================
        // SAME LINE
        // =======================
        if (sameLine) {
            List<Job> jobs = new ArrayList<>(fromLine.getJobs());

            List<Job> moved = new ArrayList<>(jobs.subList(fromIndex, fromEnd));
            jobs.subList(fromIndex, fromEnd).clear();

            insertIndex = Math.clamp(insertIndex, 0, jobs.size());
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

        insertIndex = Math.clamp(insertIndex, 0, toJobs.size());
        toJobs.addAll(insertIndex, jobsToMove);

        fromLine.setJobs(fromJobs);
        toLine.setJobs(toJobs);

        return jobsToMove;
    }
}