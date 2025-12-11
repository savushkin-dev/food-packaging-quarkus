package org.acme.foodpackaging.scheduleOperations.utils;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.LoadDTO;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

        for(Line line : lines){
            line.setFirstUnpinnedIndex(line.getJobs().size());
        }
    }

    public static void unPinnAllLines(List<Line> lines){
        for(Line line : lines){
            line.setFirstUnpinnedIndex(0);
        }
    }
    /**
     * Проверяет был ли уже план с такими же входными данными
     */
    public static boolean isScheduleCompatible(PackagingSchedule schedule, LoadDTO loadDTO) {
        if (schedule.getLines().size() != loadDTO.getLineStartTimes().size()) {
            return false;
        }

        if (!Objects.equals((schedule.getJobs().getFirst().getMaxEndTime()), loadDTO.getMaxEndDateTime())) return false;
        if (!Objects.equals((schedule.getJobs().getFirst().getIdealEndTime()), loadDTO.getIdealEndDateTime())) return false;

        Map<String, LocalDateTime> startTimesFromJson = loadDTO.toLineStartDateTimeMap();

        for (Line line : schedule.getLines()) {
            LocalTime lineStartTime = line.getStartDateTime().toLocalTime();
            LocalTime expectedStart = startTimesFromJson.get(line.getId()).toLocalTime();

            if (!lineStartTime.equals(expectedStart)) {
                return false;
            }
        }
        return true;
    }
}
