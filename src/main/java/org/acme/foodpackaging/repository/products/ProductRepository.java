package org.acme.foodpackaging.repository.products;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.NS_McEntity;
import org.acme.foodpackaging.entity.products.ProductEntity;
import org.acme.foodpackaging.domain.Product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProductRepository {

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
                    p.kmc, n.krkmc, p.ean13,
                    p.type, p.glaze, p.mass, p.filling
            );

            result.put(p.kmc, product);
        }
        return result;
    }
}
