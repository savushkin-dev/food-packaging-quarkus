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

@ApplicationScoped
public class ScheduleBuilder {

    @Inject
    JobRepository jobRepository;
    @Inject
    JobService jobService;
    @Inject
    LineService lineService;
    @Inject
    LineSchedulingService lineSchedulingService;
    @Inject
    ProductService productService;

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
        jobService.initFactProductionData(schedule, jobRepository.getFactProductionRowMap(startDate));
        List<Line> lines = lineService.getLines();
        List<Product> products = productService.getProductList(schedule);
        schedule.setLines(lines);
        schedule.setProducts(products);
        lineSchedulingService.initJobListOnLine(schedule);
        schedule.setDateForEmptySolution(startDate);

        return schedule;
    }

    public PackagingSchedule updateProductList(PackagingSchedule schedule){
        List<Product> updatedProductList = productService.getProductList(schedule);
        schedule.setProducts(updatedProductList);
        return schedule;
    }
}

