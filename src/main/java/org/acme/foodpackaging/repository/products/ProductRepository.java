package org.acme.foodpackaging.repository.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.entity.products.NS_McEntity;
import org.acme.foodpackaging.entity.products.ProductEntity;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;

import java.util.*;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.MaintenanceJob.getMaintenanceProduct;

@ApplicationScoped
public class ProductRepository {

    @Inject
    CleaningCalculatorService cleaningCalculator;

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

    public List<Product> getProductList(List<Job> jobs) {

        List<Product> productList = jobs.stream()
                .filter(j -> !j.isMaintenance())
                .map(Job::getProduct)
                .distinct()
                .collect(Collectors.toList());

        Product maintenanceProduct = getMaintenanceProduct();
        productList.add(maintenanceProduct);

        jobs.stream()
                .filter(Job::isMaintenance)
                .forEach(j -> j.setProduct(maintenanceProduct));

        cleaningCalculator.cleaningCalculate(productList);
        return productList;
    }
}
