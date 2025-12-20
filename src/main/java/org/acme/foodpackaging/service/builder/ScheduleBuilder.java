package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;

import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class ScheduleBuilder {

    @Inject
    JobRepository jobRepository;
    @Inject
    LineRepository lineRepository;
    @Inject
    ProductRepository productRepository;

    public PackagingSchedule buildSchedule(LocalDate startDate) {

        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setWorkCalendar(new WorkCalendar(startDate));

        schedule.setDbJobRowMap(jobRepository.getDbJobRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );
        schedule.setDbMaintenanceRowMap(jobRepository.getDbMaintenanceRowMap(
                schedule.getWorkCalendar().getFromDate(), schedule.getWorkCalendar().getToDate())
        );

        jobRepository.initSolutionJobList(schedule);
        List<Line> lines = lineRepository.getLines();
        List<Product> products = productRepository.getProductList(schedule);
        schedule.setLines(lines);
        schedule.setProducts(products);
        lineRepository.initJobListOnLine(schedule);

        return schedule;
    }

    public PackagingSchedule updateProductList(PackagingSchedule schedule){
        List<Product> updatedProductList = productRepository.getProductList(schedule);
        schedule.setProducts(updatedProductList);
        return schedule;
    }
}

