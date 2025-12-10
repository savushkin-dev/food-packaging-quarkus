package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.factory.LineFactory;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.getMaintenanceProduct;

@ApplicationScoped
public class ScheduleBuilder {

    @Inject
    LoadDataService loadDataService;
    @Inject
    JobRepository jobRepository;
    @Inject
    LineFactory lineFactory;
    @Inject
    CleaningCalculatorService cleaningCalculatorService;

    public PackagingSchedule buildSchedule(LoadDTO loadDTO, Map<String, LocalDateTime> lineStartsTime) {

        LocalDate startDate = loadDTO.getStartDate();
        LocalDate endDate = loadDTO.getEndDate();
        LocalDateTime minStart = Collections.min(lineStartsTime.values());
        LocalDateTime idealEnd = loadDTO.getIdealEndDateTime();
        LocalDateTime maxEnd = loadDTO.getMaxEndDateTime();

        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setWorkCalendar(new WorkCalendar(startDate, endDate, minStart, idealEnd, maxEnd));

        List<Line> lines = lineStartsTime.entrySet().stream()
                .map(e -> lineFactory.createLine(e.getKey(), e.getValue()))
                .toList();
        schedule.setLines(lines);

        List<Job> jobs = jobRepository.loadJobs(startDate, minStart, idealEnd, maxEnd);
        schedule.setJobs(jobs);

        Set<Product> productSet = jobs.stream()
                .map(Job::getProduct)
                .collect(Collectors.toSet());
        schedule.setProducts(new ArrayList<>(productSet));
        schedule.getProducts().add(getMaintenanceProduct());

        cleaningCalculatorService.cleaningCalculate(schedule.getProducts());

        return schedule;
    }
}

