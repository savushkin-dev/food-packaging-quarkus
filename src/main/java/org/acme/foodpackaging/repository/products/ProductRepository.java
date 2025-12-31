package org.acme.foodpackaging.repository.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.ProductRow;

import java.util.*;
import java.util.stream.Collectors;
import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.getMaintenanceProduct;

@ApplicationScoped
public class ProductRepository {

    @Inject
    CleaningCalculatorService cleaningCalculator;
    @Inject
    LoadDataService loadDataService;
    @Inject
    EntityManager entityManager;
/**
 * Загружает правила мойки из базы данных.
 * Каждый ряд в таблице описывает:
 * NPAR — параметр (1–тип, 2–глазурь, 3–масса, 4–наполнитель)
 * FROM_VALUE — значение параметра исходного продукта
 * TO_VALUE — значение параметра целевого продукта
 * DUR — длительность мойки
 * */
public Map<String, Product> loadProducts() {

    List<ProductRow> rows = entityManager.createQuery("""
        select new org.acme.foodpackaging.record.ProductRow(
            p.kmc, p.ean13, p.type, p.glaze,
            p.mass, p.filling, p.ns.shortName,
            p.ns.krkmc
        )
        from ProductEntity p
        join p.ns n
        where p.deletedFlag = 0
    """, ProductRow.class).getResultList();

    Map<String, Product> result = new HashMap<>(rows.size());

    for (ProductRow r : rows) {
        Product product = new Product(
                r.shortName(), r.kmc(),
                r.krkmc(), r.type(),
                r.glaze(), r.mass(), r.filling()
        );
        result.put(r.kmc(), product);
    }

    return result;
}
/**
 * Формирует список продуктов, реально используемых в решении,
 * + добавляет сервисный (maintenance) продукт.
 */
public List<Product> getProductList(PackagingSchedule solution) {

    Map<String, Product> products = loadDataService.getProducts();

    List<Product> productList =
            solution.getDbJobRowMap().values().stream()
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
