package org.acme.foodpackaging.repository.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.products.NS_McEntity;
import org.acme.foodpackaging.entity.products.ProductEntity;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.acme.foodpackaging.record.DbJobRow;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.getMaintenanceProduct;

@ApplicationScoped
public class ProductRepository {

    @Inject
    CleaningCalculatorService cleaningCalculator;
    @Inject
    LoadDataService loadDataService;

/**
 * Загружает правила мойки из базы данных.
 * Каждый ряд в таблице описывает:
 * NPAR — параметр (1–тип, 2–глазурь, 3–масса, 4–наполнитель)
 * FROM_VALUE — значение параметра исходного продукта
 * TO_VALUE — значение параметра целевого продукта
 * DUR — длительность мойки
 * */
    public Map<String, Product> loadProducts() {

        List<ProductEntity> rows = ProductEntity.find("deletedFlag = 0").list();

        Map<String, Product> result = new HashMap<>();

        for (ProductEntity p : rows) {

            NS_McEntity n = p.ns;
            if (n == null) {
                throw new IllegalStateException("Missing NS_MC record for KMC=" + p.kmc);
            }

            Product product = new Product(
                    n.shortName,
                    p.kmc, n.krkmc,
                    p.type, p.glaze, p.mass, p.filling
            );

            result.put(p.kmc, product);
        }
        return result;
    }

    public List<Product> getProductList(PackagingSchedule solution) {

        List<Product> productList =
                solution.getDbJobRowMap().values().stream()
                        .map(DbJobRow::kmc)
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(kmc -> {
                            Product product = loadDataService
                                    .getProducts()
                                    .get(kmc);

                            if (product == null) {
                                throw new IllegalStateException(
                                        "Product not found for KMC: " + kmc
                                );
                            }
                            return product;
                        })
                        .collect(Collectors.toCollection(ArrayList::new));

        // --- Maintenance ---
        Product maintenanceProduct = getMaintenanceProduct();
        productList.add(maintenanceProduct);

        if (!solution.getJobs().isEmpty()) {
            solution.getJobs().stream()
                    .filter(Job::isMaintenance)
                    .forEach(j -> j.setProduct(maintenanceProduct));
        }

        cleaningCalculator.cleaningCalculate(productList, loadDataService.getCleaningRules());
        return productList;
    }

}
