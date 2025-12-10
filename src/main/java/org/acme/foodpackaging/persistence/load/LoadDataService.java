package org.acme.foodpackaging.persistence.load;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.repository.products.CleaningRuleRepository;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.repository.lines.SpeedRepository;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;

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
}


