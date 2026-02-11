package org.acme.foodpackaging.repository.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.ProductRow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access repository for products.
 * Handles JPA queries and database operations for products.
 */
@ApplicationScoped
public class ProductRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Загружает продукты из базы данных.
     * Выполняет JOIN между PlrMc и NsMc для получения полной информации о продуктах.
     * 
     * @return Map продуктов по KMC, исключая удаленные (deletedFlag = 0)
     */
    public Map<String, Product> loadProducts() {
        List<ProductRow> rows = entityManager.createQuery("""
            select new org.acme.foodpackaging.record.ProductRow(
                p.kmc, p.ean13, p.type, p.glaze,
                p.mass, p.filling, p.ns.shortName,
                p.ns.krkmc, p.ns.massa
            )
            from PlrMc p
            join p.ns n
            where p.deletedFlag = 0
        """, ProductRow.class).getResultList();

        Map<String, Product> result = new HashMap<>(rows.size());

        for (ProductRow r : rows) {
            Product product = new Product(
                    r.shortName(), r.kmc(),
                    r.krkmc(), r.type(),
                    r.glaze(), r.mass(), r.filling(),
                    r.ean13(), r.massa()
            );
            result.put(r.kmc(), product);
        }

        return result;
    }
}
