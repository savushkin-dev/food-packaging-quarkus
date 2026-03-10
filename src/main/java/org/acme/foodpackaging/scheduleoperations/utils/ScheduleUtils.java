package org.acme.foodpackaging.scheduleoperations.utils;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.record.DbJobRow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class ScheduleUtils {

    public static final int START_FACT_EVENT_TYPE = 1;
    public static final int START_CAMERA_EVENT_TYPE = 2;
    public static final int END_CAMERA_EVENT_TYPE = 3;

    private ScheduleUtils() {}
    /**
     * Восстанавливает previous/next и пересчитывает shadow variables в линии
     */
    public static void fixLineJobs(Line line) {
        List<Job> jobs = line.getJobs();
        for (int i = 0; i < jobs.size(); i++) {
            Job current = jobs.get(i);
            current.setLine(line);
            current.setPreviousJob(i > 0 ? jobs.get(i - 1) : null);
            current.setNextJob(i < jobs.size() - 1 ? jobs.get(i + 1) : null);
            current.updateStartCleaningDateTime();
        }
    }
    /**
     * Закрпеляет все что стоит до ремонтной работы, включая саму ремонтную работу
     */
    public static void fixPinnedJobs(Line line) {
        List<Job> jobs = line.getJobs();

        int lastPinnedIndex = -1;

        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);

            // Старые задачи всегда pinned
            if (job.getLineId() != null) {
                lastPinnedIndex = i;
                continue;
            }

            // Maintenance тоже pinned
            if (job.isMaintenance()) {
                lastPinnedIndex = i;
            }
        }

        line.setFirstUnpinnedIndex(lastPinnedIndex + 1);
    }
    /**
     * Назначает общий maxEndDateTime для всех задач
     */
    public static void fixEndDateTime(List<Job> jobs, LocalDateTime maxEndDateTime) {
        for(Job job : jobs){
            job.setMaxEndTime(maxEndDateTime);
        }
    }
    /**
     * Поиск линии в schedule по id
     */
    public static Line findLineById(PackagingSchedule schedule, String id){
        return schedule.getLines().stream()
                .filter(l -> l.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    /**
     * Меняет время старта линии
     */
    public static void setLineStartDateTime(Line line, LocalDateTime lineStartDateTime) {
        line.setStartDateTime(lineStartDateTime);
    }
    /**
     * Меняет максимальное время завершения работы линии
     */
    public static void setLineMaxEndDateTime(Line line, LocalDateTime lineMaxEndDateTime) {
        line.setMaxEndTime(lineMaxEndDateTime);
    }
    /**
     * Закрепляет/Открепляет весь план
     */
    public static void pinnAllLines(List<Line> lines) {
        LocalDateTime maxEndTime = lines.stream()
                .map(Line::getJobs)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(Job::getEndDateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        for (Line line : lines) {
            if (line.getJobs() == null) line.setJobs(new ArrayList<>());
            if (maxEndTime != null) {
                line.setStartDateTime(maxEndTime);
            }
            line.setFirstUnpinnedIndex(line.getJobs().size());
        }
    }

    public static void unPinnAllLines(List<Line> lines) {
        for (Line line : lines) {
            line.setFirstUnpinnedIndex(0);
        }
    }

    private static final Pattern NAME_CLEANER_PATTERN = Pattern.compile(
            "Сырок\\s*(тв\\.\\s*г\\.с?|тв\\.\\s*гл\\.с?|гл\\.|тв\\.\\s*глазированный|глазированный|тв\\.\\s*глазир\\.)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ
    );

    public static String nameCleaner(String input) {
        return NAME_CLEANER_PATTERN.matcher(input).replaceFirst("").trim();
    }

    /**
     * Удаляет задачи, у которых line равен null, из списка задач.
     * 
     * @param jobs Список задач для фильтрации
     */
    public static void removeJobsWithoutLine(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        jobs.removeIf(job -> job.getLine() == null);
    }

     /**
     * Преобразует Map в List для удобства работы.
     * 
     * @param rows Map of job rows
     * @return List of job rows
     */
     public static List<DbJobRow> getDbJobRowList(Map<Long, DbJobRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(rows.values());
    }
}
