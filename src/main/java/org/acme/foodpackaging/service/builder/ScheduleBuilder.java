package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.factory.LineFactory;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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

    public PackagingSchedule buildSchedule(LocalDate startDate) {

        LocalDate endDate = startDate.plusDays(2);
        LocalDateTime minStart = startDate.minusDays(1).atStartOfDay();
        LocalDateTime idealEnd = endDate.atStartOfDay().plusHours(2);
        LocalDateTime maxEnd = endDate.atStartOfDay().plusHours(3);

        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setWorkCalendar(new WorkCalendar(startDate, endDate, minStart, idealEnd, maxEnd));
        jobRepository.init(startDate);

        List<Line> lines = lineRepository.getLines();
        List<Job> jobs = jobRepository.getJobs();
        List<Product> products = productRepository.getProductList(jobs);

        lineRepository.initJobListOnLine(lines, jobs);

        schedule.setLines(lines);
        schedule.setJobs(jobs);
        schedule.setProducts(products);

        return schedule;
    }

    public PackagingSchedule updateProductList(PackagingSchedule schedule){
        List<Product> updatedProductList = productRepository.getProductList(schedule.getJobs());
        schedule.setProducts(updatedProductList);
        return schedule;
    }
}

