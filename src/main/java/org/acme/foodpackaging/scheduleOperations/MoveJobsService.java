package org.acme.foodpackaging.scheduleOperations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MoveJobsRequestDTO;
import org.acme.foodpackaging.service.load.LoadDataService;

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
    public PackagingSchedule moveJobs(PackagingSchedule schedule, MoveJobsRequestDTO request) {
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
                Integer duration = loadDataService.getLineSpeeds()
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
        if (sameLine && insertIndex >= fromIndex && insertIndex <= fromEnd) {
            return schedule;
        }

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
     * Перемещает подсписок из одного списка в другой и возвращает перемещённые задачи.
     * Метод работает на копиях списков, затем устанавливает новые списки в объекты Line.
     */
    private List<Job> moveSubList(Line fromLine, int fromIndex, int count,
                                  Line toLine, int insertIndex) {

        boolean sameLine = fromLine.getId().equals(toLine.getId());

        List<Job> fromJobs = new ArrayList<>(Optional.ofNullable(fromLine.getJobs()).orElse(Collections.emptyList()));
        List<Job> toJobs = sameLine
                ? fromJobs
                : new ArrayList<>(Optional.ofNullable(toLine.getJobs()).orElse(Collections.emptyList()));


        int fromEnd = Math.min(fromIndex + Math.max(0, count), fromJobs.size());
        if (fromIndex < 0 || fromIndex >= fromJobs.size() || fromIndex >= fromEnd) {
            return Collections.emptyList();
        }

        List<Job> jobsToMove = new ArrayList<>(fromJobs.subList(fromIndex, fromEnd));

        for (int i = 0; i < jobsToMove.size(); i++) {
            fromJobs.remove(fromIndex);
        }

        if (sameLine && insertIndex > fromIndex) {
            insertIndex -= jobsToMove.size();
        }

        insertIndex = Math.max(0, Math.min(insertIndex, toJobs.size()));

        List<Job> newToJobs = new ArrayList<>(toJobs.size() + jobsToMove.size());
        for (int i = 0; i < toJobs.size(); i++) {
            if (i == insertIndex) {
                newToJobs.addAll(jobsToMove);
            }
            newToJobs.add(toJobs.get(i));
        }
        if (insertIndex == toJobs.size()) {
            newToJobs.addAll(jobsToMove);
        }

        fromLine.setJobs(fromJobs);
        if (!sameLine) {
            toLine.setJobs(newToJobs);
        } else {
            fromLine.setJobs(newToJobs);
        }

        return jobsToMove;
    }
}

