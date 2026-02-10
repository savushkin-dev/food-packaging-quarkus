package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.lines.LineSchedulingService;
import org.acme.foodpackaging.service.lines.LineService;

import java.time.LocalDate;
import java.util.*;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.removeJobsWithoutLine;

@ApplicationScoped
public class ScheduleBuilder {

    @Inject
    public ScheduleBuilder(JobRepository jobRepository, JobService jobService, LineService lineService, LineSchedulingService lineSchedulingService, ProductService productService, JobRefreshService jobRefreshService) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.lineService = lineService;
        this.lineSchedulingService = lineSchedulingService;
        this.productService = productService;
        this.jobRefreshService = jobRefreshService;
    }
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final LineService lineService;
    private final LineSchedulingService lineSchedulingService;
    private final ProductService productService;
    private final JobRefreshService jobRefreshService;

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
        jobService.initFactProductionData(schedule, jobRepository.getFactProductionRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );

        jobService.enrichCameraFactsFromPmLog(schedule);
        jobRefreshService.refreshStaleCameraEndFromPmLog(schedule);

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