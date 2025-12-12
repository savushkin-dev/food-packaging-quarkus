package org.acme.foodpackaging.persistence.load;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.scheduleSaving.SolutionExport;
import org.acme.foodpackaging.persistence.scheduleSaving.SolutionImport;
import org.acme.foodpackaging.repository.products.CleaningRuleRepository;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.repository.lines.SpeedRepository;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class LoadDataService {

    @Inject
    LineRepository lineRepository;
    @Inject
    ProductRepository productRepository;
    @Inject
    SpeedRepository speedRepository;
    @Inject
    CleaningRuleRepository cleaningRuleRepository;
    @Inject
    CleaningCalculatorService cleaningCalculator;
    @Inject
    SolutionImport solutionImport;
    @Inject
    JobRefreshService jobRefreshService;

    @Getter
    private ConcurrentMap<String, String> lines;
    @Getter
    private Map<String, Product> products;
    @Getter
    private Map<String, Map<String, Integer>> lineSpeeds;
    @Getter
    private List<CleaningRule> cleaningRules;

    @PostConstruct
    void init() {
        this.lines = lineRepository.loadLines();
        this.products = productRepository.loadProducts();
        this.cleaningRules = cleaningRuleRepository.loadRules();
        SpeedCacheUtils.init(speedRepository.createSpeedMap());
    }

    public PackagingSchedule loadScheduleFromDb(LocalDate date) {
        return solutionImport.importFromDb(date);
    }

    public void refreshJobsNextDay(PackagingSchedule schedule) {
        jobRefreshService.refreshJobsNextDay(schedule);
    }
}


