package org.acme.foodpackaging.persistence.load;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.entity.lines.PlrLines;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.lines.SpeedRepository;
import org.acme.foodpackaging.repository.products.CleaningRuleRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class LoadDataService {

    @Inject
    LineRepository lineRepository;
    @Inject
    ProductRepository productRepository;
    @Inject
    CleaningRuleRepository cleaningRuleRepository;

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
        
        List<PlrLines> allLineEntities = lineRepository.find("fDel = 0").list();
       
        this.lines = allLineEntities.stream()
                .filter(e -> e.getSnm() != null)
                .collect(Collectors.toConcurrentMap(
                        e -> e.getLineId().trim(),
                        e -> e.getSnm().trim(),
                        (existing, ignored) -> existing
                ));
        
        Map<SpeedRepository.LineTypeKey, Integer> rawSpeeds = allLineEntities.stream()
                .filter(e -> e.getSpeed() != null)
                .collect(Collectors.toMap(
                        e -> new SpeedRepository.LineTypeKey(
                                e.getLineId().trim(),
                                e.getType().trim()
                        ),
                        PlrLines::getSpeed,
                        (existing, ignored) -> existing
                ));
        
        this.lineSpeeds = SpeedRepository.createSpeedMap(rawSpeeds);
        SpeedCacheUtils.init(this.lineSpeeds);
        
        this.products = productRepository.loadProducts();
        this.cleaningRules = cleaningRuleRepository.loadRules();
    }
}


