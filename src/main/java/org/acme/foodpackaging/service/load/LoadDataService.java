package org.acme.foodpackaging.service.load;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.repository.CleaningRuleRepository;
import org.acme.foodpackaging.repository.LineRepository;
import org.acme.foodpackaging.repository.ProductRepository;
import org.acme.foodpackaging.repository.SpeedRepository;
import org.acme.foodpackaging.service.CleaningCalculatorService;
import org.apache.commons.math3.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Map<String, String> lines;
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
        this.lineSpeeds = convert(speedRepository.loadSpeeds());
    }

    private Map<String, Map<String, Integer>> convert(Map<Pair<String, String>, Integer> raw) {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        for (var e : raw.entrySet()) {
            String line = e.getKey().getFirst();
            String type = e.getKey().getSecond();
            int speed = e.getValue();
            map.computeIfAbsent(line, v -> new HashMap<>()).put(type, speed);
        }
        return map;
    }
}


