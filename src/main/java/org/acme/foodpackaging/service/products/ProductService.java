package org.acme.foodpackaging.service.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.getMaintenanceProduct;

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
        Map<String, Product> products = loadDataService.getProducts();

        List<Product> productList = solution.getDbJobRowMap().values().stream()
                .map(DbJobRow::kmc)
                .filter(Objects::nonNull)
                .distinct()
                .map(kmc -> {
                    Product product = products.get(kmc);
                    if (product == null) {
                        throw new IllegalStateException(
                                "Product not found for KMC: " + kmc
                        );
                    }
                    return product;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // --- Maintenance product ---
        Product maintenanceProduct = getMaintenanceProduct();
        productList.add(maintenanceProduct);

        if (!solution.getJobs().isEmpty()) {
            solution.getJobs().stream()
                    .filter(Job::isMaintenance)
                    .forEach(j -> j.setProduct(maintenanceProduct));
        }

        // --- Cleaning rules ---
        cleaningCalculator.setRules(loadDataService.getCleaningRules());
        cleaningCalculator.cleaningCalculate(productList);

        return productList;
    }
}
