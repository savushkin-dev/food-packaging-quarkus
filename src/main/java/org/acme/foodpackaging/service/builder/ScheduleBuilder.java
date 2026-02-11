package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.InitData;
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

    public InitData buildSchedule(LocalDate startDate) {

        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setWorkCalendar(new WorkCalendar(startDate));
        schedule.setLines(lineService.getLines());

         Map<Long, DbJobRow> jobRows = jobService.initSolutionJobList(schedule);
        jobService.initFactProductionData(schedule, jobRepository.getFactProductionRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );

        jobService.enrichCameraFactsFromPmLog(schedule);
        jobRefreshService.refreshStaleCameraEndFromPmLog(schedule);
        lineSchedulingService.initJobListOnLine(schedule);
        List<Product> products = productService.getProductList(schedule);
        schedule.setProducts(products);

        schedule.setDateForEmptySolution(startDate);
        removeJobsWithoutLine(schedule.getJobs());

        return new InitData(schedule, jobRows);
    }

    public PackagingSchedule updateProductList(PackagingSchedule schedule){
        List<Product> updatedProductList = productService.getProductList(schedule);
        schedule.setProducts(updatedProductList);
        return schedule;
    }
}