package org.acme.foodpackaging.repository.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.row.products.ProductRow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access repository for products.
 * Handles JPA queries and database operations for products.
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProductRepository {
    
    private  final EntityManager entityManager;

    /**
     * Загружает продукты из базы данных.
     * Выполняет JOIN между PlrMc и NsMc для получения полной информации о продуктах.
     * 
     * @return Map продуктов по KMC, исключая удаленные (deletedFlag = 0)
     */
    public Map<String, Product> loadProducts() {
        List<ProductRow> rows = entityManager.createQuery("""
            select new ProductRow(
                p.kmc, p.ean13, p.type, p.glaze,
                p.mass, p.filling, p.ns.shortName,
                p.ns.krkmc, p.ns.massa
            )
            from PlrMc p
            join p.ns n
            where p.deletedFlag = 0
        """, ProductRow.class).getResultList();

        Map<String, Product> result = HashMap.newHashMap(rows.size());

        for (ProductRow r : rows) {
            Product product = new Product(
                    r.shortName(), r.kmc(),
                    r.krkmc(), r.type(),
                    r.glaze(), r.mass(), r.filling()
            );
            product.setEan13(r.ean13());
            product.setMass(r.massa());

            result.put(r.kmc(), product);
        }

        return result;
    }
}
