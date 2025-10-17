package org.acme.foodpackaging.persistence;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;

import java.util.*;

public class SolutionPostProcessor {

    public static void sortJobsByNp(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            List<Job> originalJobs = new ArrayList<>(line.getJobs());
            List<Job> newOrder = new ArrayList<>();
            List<Job> buffer = new ArrayList<>();

            for (Job job : originalJobs) {
                if (hadCleaningBefore(job)) {
                    if (!buffer.isEmpty()) {
                        newOrder.addAll(sortProductGroupsByNp(buffer));
                        buffer.clear();
                    }
                }
                buffer.add(job);
            }

            if (!buffer.isEmpty()) {
                newOrder.addAll(sortProductGroupsByNp(buffer));
            }

            for (int i = 0; i < newOrder.size(); i++) {
                Job current = newOrder.get(i);
                Job prev = (i > 0) ? newOrder.get(i - 1) : null;
                Job next = (i < newOrder.size() - 1) ? newOrder.get(i + 1) : null;
                current.setPreviousJob(prev);
                current.setNextJob(next);
            }

            for (Job job : newOrder) {
                job.updateStartCleaningDateTime();
            }

            line.setJobs(newOrder);
        }
    }

    private static boolean hadCleaningBefore(Job job) {
        if (job.getStartCleaningDateTime() == null || job.getStartProductionDateTime() == null) return false;
        return job.getStartCleaningDateTime().isBefore(job.getStartProductionDateTime());
    }

    private static List<Job> sortProductGroupsByNp(List<Job> jobs) {
        List<Job> result = new ArrayList<>();
        if (jobs.isEmpty()) return result;

        Map<Product, List<Job>> groupedByProduct = new LinkedHashMap<>();
        for (Job job : jobs) {
            groupedByProduct
                    .computeIfAbsent(job.getProduct(), p -> new ArrayList<>())
                    .add(job);
        }

        for (List<Job> group : groupedByProduct.values()) {
            group.sort(Comparator.comparing(Job::getNp));
            result.addAll(group);
        }

        return result;
    }
}
