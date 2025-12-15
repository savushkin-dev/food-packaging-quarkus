package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.factory.JobFactory;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.persistence.load.UpdateJobLoaderService;
import org.acme.foodpackaging.record.DbJobInfo;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.pinnAllLines;

@ApplicationScoped
public class JobRefreshService {

    @Inject
    UpdateJobLoaderService jobLoader;
    @Inject
    CleaningCalculatorService cleaningCalculator;
    @Inject
    LoadDataService loadDataService;
    @Inject
    JobFactory jobFactory;

    public void refreshJobsNextDay(PackagingSchedule schedule) {

        LocalDate planningDay = schedule.getWorkCalendar().getFromDate();
        Map<Integer, DbJobInfo> dbJobsNextDay = jobLoader.loadDbJobInfo(planningDay);

        List<Job> currentJobs = schedule.getJobs();

        Map<Integer, Job> scheduleMap = currentJobs.stream().filter(j -> !j.isMaintenance())
                .collect(Collectors.toMap(Job::getSnpz, j -> j));

        List<Job> toAdd = new ArrayList<>();
        List<Job> toRemove = new ArrayList<>();

        boolean newProductsAppeared = false;

        for (DbJobInfo info : dbJobsNextDay.values()) {

            if (!scheduleMap.containsKey(info.snpz())) {

                Product product = schedule.getProducts().stream()
                        .filter(p -> p.getId().equals(info.kmc()))
                        .findFirst()
                        .orElse(loadDataService.getProducts().get(info.kmc()));

                if (product == null) {
                    throw new IllegalStateException("Unknown product KMC=" + info.kmc());
                }

                if (!schedule.getProducts().contains(product)) {
                    schedule.getProducts().add(product);
                    newProductsAppeared = true;
                }

                Job newJob = jobFactory.createJob(
                        String.valueOf(info.snpz()),info.snpz(), info.np(),
                        info.shortName(), product, info.mass(),info.quantity(), 15,
                        schedule.getWorkCalendar().getMinStartDateTime(),
                        schedule.getWorkCalendar().getIdealEndDateTime(),
                        schedule.getWorkCalendar().getMaxEndDateTime(),
                        info.priority(), LocalDateTime.now()
                );

                toAdd.add(newJob);
            }
        }

        for (Job job : currentJobs) {
            if (!dbJobsNextDay.containsKey(job.getSnpz()) && !job.isMaintenance()) {
                toRemove.add(job);
            }
        }

        for (Job jobToRemove : toRemove) {
            schedule.getJobs().remove(jobToRemove);

            for (Line line : schedule.getLines()) {
                int index = line.getJobs().indexOf(jobToRemove);
                if (index >= 0) {
                    line.getJobs().remove(index);
                    fixLineJobs(line);
                }
            }
        }

        pinnAllLines(schedule.getLines());
        schedule.getJobs().addAll(toAdd);

        if (newProductsAppeared) {
            cleaningCalculator.cleaningCalculate(schedule.getProducts());
        }
    }
}
