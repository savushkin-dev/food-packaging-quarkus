package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.record.InitData;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.lines.LineService;

import java.time.LocalDate;
import java.util.*;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
public class ScheduleBuilder {

    @Inject
    public ScheduleBuilder(JobService jobService, LineService lineService,
                           ProductService productService, AlignSolutionService alignSolutionService) {
        this.jobService = jobService;
        this.lineService = lineService;
        this.productService = productService;
        this.alignSolutionService = alignSolutionService;
    }

    private final JobService jobService;
    private final LineService lineService;
    private final ProductService productService;
    private final AlignSolutionService alignSolutionService;

    public InitData buildSchedule(LocalDate startDate) {

        PackagingSchedule schedule = new PackagingSchedule(lineService.getLines(), startDate);
        List<JobRow> jobRows = jobService.buildJobsOnLines(schedule);
        productService.buildProducts(schedule);

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

