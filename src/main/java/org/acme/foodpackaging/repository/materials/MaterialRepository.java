package org.acme.foodpackaging.repository.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.acme.foodpackaging.dto.materials.ProductDto;
import org.acme.foodpackaging.sql.SqlQueries;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class MaterialRepository {

    @Inject
    EntityManager em;

    @Inject
    SqlQueries sqlQueries;

    @ConfigProperty(name = "ksk")
    String defaultKsk;

    @SuppressWarnings("unchecked")
    public List<ProductDto> findProductsByDate(String date) {
        String sql = sqlQueries.loadProductsGroupedByDti();

        Query query = em.createNativeQuery(sql);

        query.setParameter(1, date);
        query.setParameter(2, defaultKsk);

        List<Object[]> results = query.getResultList();

        List<ProductDto> products = new ArrayList<>();
        for (Object[] row : results) {
            ProductDto product = new ProductDto();
            product.setKmc((String) row[0]);
            product.setEan13((String) row[1]);
            product.setEmk(((BigDecimal) row[2]).doubleValue());
            product.setKt((String) row[3]);
            product.setSumMass(((BigDecimal) row[4]).doubleValue());
            product.setSumKolev(((BigDecimal) row[5]).doubleValue());
            product.setProductName((String) row[6]);
            product.setKrkmc(((BigDecimal) row[7]).doubleValue());
            products.add(product);
        }

        return products;
    }
}