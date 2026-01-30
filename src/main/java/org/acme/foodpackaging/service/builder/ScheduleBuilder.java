package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.lines.LineSchedulingService;
import org.acme.foodpackaging.service.lines.LineService;

import java.time.LocalDate;
import java.util.*;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.removeJobsWithoutLine;

@ApplicationScoped
public class ScheduleBuilder {

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final LineService lineService;
    private final LineSchedulingService lineSchedulingService;
    private final ProductService productService;

    @Inject
    public ScheduleBuilder(
            JobRepository jobRepository,
            JobService jobService,
            LineService lineService,
            LineSchedulingService lineSchedulingService,
            ProductService productService
    ) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.lineService = lineService;
        this.lineSchedulingService = lineSchedulingService;
        this.productService = productService;
    }

    public PackagingSchedule buildSchedule(LocalDate startDate) {

        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setWorkCalendar(new WorkCalendar(startDate));

        schedule.setDbJobRowMap(jobRepository.getDbJobRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );
        schedule.setDbMaintenanceRowMap(jobRepository.getDbMaintenanceRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );

        jobService.initSolutionJobList(schedule);
        // Load all events together and initialize facts + camera with fallback & persistence
        var msLogEvents = jobRepository.getMsLogEvents(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate());
        jobService.initFromMsLogEvents(schedule, msLogEvents);
        
        List<Line> lines = lineService.getLines();
        List<Product> products = productService.getProductList(schedule);
        schedule.setLines(lines);
        schedule.setProducts(products);
        lineSchedulingService.initJobListOnLine(schedule);
        schedule.setDateForEmptySolution(startDate);
        removeJobsWithoutLine(schedule.getJobs());
        
        return schedule;
    }

    public PackagingSchedule updateProductList(PackagingSchedule schedule){
        List<Product> updatedProductList = productService.getProductList(schedule);
        schedule.setProducts(updatedProductList);
        return schedule;
    }
}

