package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.InitData;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.lines.LineService;

import java.time.LocalDate;
import java.util.*;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.removeJobsWithoutLine;

@ApplicationScoped
public class ScheduleBuilder {

    @Inject
    public ScheduleBuilder(JobRepository jobRepository, JobService jobService, LineService lineService,
                           ProductService productService, JobRefreshService jobRefreshService, AlignSolutionService alignSolutionService) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.lineService = lineService;
        this.productService = productService;
        this.jobRefreshService = jobRefreshService;
        this.alignSolutionService = alignSolutionService;
    }
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final LineService lineService;
    private final ProductService productService;
    private final JobRefreshService jobRefreshService;
    private final AlignSolutionService alignSolutionService;

    public InitData buildSchedule(LocalDate startDate) {

        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setWorkCalendar(new WorkCalendar(startDate));
        schedule.setLines(lineService.getLines());
        schedule.setDti(startDate);

        List<DbJobRow> jobRows = jobService.initSolutionJobList(schedule);
        jobService.initFactProductionData(schedule, jobRepository.getFactProductionRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );

        jobService.enrichCameraFactsFromPmLog(schedule);
        jobService.initIdBatch(schedule);
        jobRefreshService.refreshStaleCameraEndFromPmLog(schedule);
        lineService.initLineStartEnd(schedule);
        List<Product> products = productService.getProductList(schedule);
        schedule.setProducts(products);

        schedule.setDateForEmptySolution(startDate);
        removeJobsWithoutLine(schedule.getJobs());

        alignSolutionService.align(schedule);

        return new InitData(schedule, jobRows);
    }

    public PackagingSchedule updateProductList(PackagingSchedule schedule){
        List<Product> updatedProductList = productService.getProductList(schedule);
        schedule.setProducts(updatedProductList);
        return schedule;
    }
}

