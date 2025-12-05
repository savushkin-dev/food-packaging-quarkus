package org.acme.foodpackaging.scheduleOperations.utils;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;

import java.util.List;

public class ScheduleFixUtils {
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

    public static void fixPinnedJobs(Line line) {
        List<Job> jobs = line.getJobs();
        line.setFirstUnpinnedIndex(0);
        for(int i = 0; i < jobs.size(); ++i){
            if(jobs.get(i).isMaintenance()) {
                line.setFirstUnpinnedIndex(i+1);
            }
        }
    }

    public static void pinnAllLines(List<Line> lines) {

        for(Line line : lines){
            line.setFirstUnpinnedIndex(line.getJobs().size());
        }
    }
}
