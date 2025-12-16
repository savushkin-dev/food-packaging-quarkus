package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.LoadDTO;
import org.acme.foodpackaging.factory.LineFactory;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.getMaintenanceProduct;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.pinnAllLines;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.setLineStartByEarliestJob;

@ApplicationScoped
public class ScheduleBuilder {

    @Inject
    LoadDataService loadDataService;
    @Inject
    JobRepository jobRepository;
    @Inject
    LineRepository lineRepository;
    @Inject
    ProductRepository productRepository;
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
        jobRepository.loadAllJobs(startDate);

        List<Line> lines = lineRepository.getLines(lineStartsTime);
        List<Job> jobs = jobRepository.getPlannedJobs();
        List<Product> products = productRepository.getProductList(jobs);

        lineRepository.initJobListOnLine(lines, jobs);

        schedule.setLines(lines);
        schedule.setJobs(jobs);
        schedule.setProducts(products);

        return schedule;
    }
}

