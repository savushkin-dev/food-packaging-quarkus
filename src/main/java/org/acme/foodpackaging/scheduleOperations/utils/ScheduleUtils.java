package org.acme.foodpackaging.scheduleOperations.utils;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.time.LocalDateTime;
import java.util.List;

public class ScheduleUtils {
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
        line.setFirstUnpinnedIndex(0);
        for(int i = 0; i < jobs.size(); ++i){
            if(jobs.get(i).isMaintenance()) {
                line.setFirstUnpinnedIndex(i+1);
            }
        }
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
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + id));
    }
    /**
     * Меняет время старта линии
     */
    public static void setLineStartDateTime(Line line, LocalDateTime lineStartDateTime) {
        line.setStartDateTime(lineStartDateTime);
    }
    /**
     * Закрепляет/Открепляет весь план
     */
    public static void pinnAllLines(List<Line> lines) {

        LocalDateTime maxEndTime = lines.stream()
                .map(Line::getJobs)
                .filter(jobs -> jobs != null && !jobs.isEmpty())
                .map(jobs -> jobs.getLast().getEndDateTime())
                .max(LocalDateTime::compareTo)
                .orElse(null);

        for (Line line : lines) {
            if (maxEndTime != null) {
                line.setStartDateTime(maxEndTime);
            }
            line.setFirstUnpinnedIndex(line.getJobs().size());
        }
    }
    /**
     * Находит время старта линии
     */
    public static void setLineStartByEarliestJob(List<Line> lines) {

        LocalDateTime minStartTime = lines.stream()
                .flatMap(line -> line.getJobs().stream())
                .map(Job::getStartProductionDateTime)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        for (Line line : lines) {
            if (minStartTime != null) {
                line.setStartDateTime(minStartTime);
            }
        }
    }

    public static void unPinnAllLines(List<Line> lines){
        for(Line line : lines){
            line.setFirstUnpinnedIndex(0);
        }
    }
    /**
     * Удяляет неназначенные задачи
     */
    public static void removeJobsWithoutLine(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        jobs.removeIf(job -> job.getLineId() == null);
    }

}
