package org.acme.foodpackaging.persistence.load;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.Getter;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.entity.lines.PlrLines;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.lines.SpeedRepository;
import org.acme.foodpackaging.repository.products.CleaningRuleRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.repository.jobs.PlrPevRepository;
import org.acme.foodpackaging.repository.lines.PlrLcRepository;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class LoadDataService {

    private final LineRepository lineRepository;
    private final ProductRepository productRepository;
    private final CleaningRuleRepository cleaningRuleRepository;
    private final PlrPevRepository plrPevRepository;
    private final PlrLcRepository plrLcRepository;

    @Getter
    private ConcurrentMap<String, String> lines;
    @Getter
    private ConcurrentMap<Integer, String> maintenanceTypes;
    @Getter
    private Map<String, Product> products;
    @Getter
    private Map<String, Map<String, Integer>> lineSpeeds;
    @Getter
    private List<CleaningRule> cleaningRules;
    @Getter
    private Map<String, Integer> linesCleaning;

    @Inject
    public LoadDataService(
            LineRepository lineRepository,
            ProductRepository productRepository,
            CleaningRuleRepository cleaningRuleRepository,
            PlrPevRepository plrPevRepository,
            PlrLcRepository plrLcRepository
    ) {
        this.lineRepository = lineRepository;
        this.productRepository = productRepository;
        this.cleaningRuleRepository = cleaningRuleRepository;
        this.plrPevRepository = plrPevRepository;
        this.plrLcRepository = plrLcRepository;
    }

    void onStart(@Observes StartupEvent ev) {
        if (LaunchMode.current() == LaunchMode.TEST) {
            return;
        }
        loadData();
    }

    public void refresh() {
        loadData();
    }

    private void loadData() {
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
        this.linesCleaning = plrLcRepository.loadLinesCleaning();
        SpeedCacheUtils.init(this.lineSpeeds);

        this.products = productRepository.loadProducts();
        this.cleaningRules = cleaningRuleRepository.loadRules();
        this.maintenanceTypes = plrPevRepository.loadMaintenanceTypesRowMap();
    }
}


