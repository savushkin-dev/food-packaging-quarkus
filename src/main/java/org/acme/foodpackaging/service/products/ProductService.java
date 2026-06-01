package org.acme.foodpackaging.service.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Business logic service for product list management.
 * Handles building product lists for schedules, including maintenance products
 * and cleaning calculations.
 */
@ApplicationScoped
public class ProductService {

    @Inject
    CleaningCalculatorService cleaningCalculator;
    @Inject
    LoadDataService loadDataService;

    /**
     * Формирует список продуктов, реально используемых в решении,
     * + добавляет сервисный (maintenance) продукт.
     *
     * @param solution The packaging schedule containing jobs
     * @return List of products used in the solution, including maintenance product
     */
    public List<Product> getProductList(PackagingSchedule solution) {

        List<Product> productList = solution.getJobs().stream()
                .filter(job -> !job.isMaintenance())
                .map(Job::getProduct)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        productList.add(solution.getMaintenanceProduct());

        solution.getJobs().stream()
                .filter(Job::isMaintenance)
                .forEach(j -> j.setProduct(solution.getMaintenanceProduct()));

        // --- Cleaning rules ---
        cleaningCalculator.setRules(loadDataService.getCleaningRules());
        cleaningCalculator.cleaningCalculate(productList);

        return productList;
    }

    public void buildProducts(PackagingSchedule schedule){
       schedule.setProducts(getProductList(schedule));
    }
}