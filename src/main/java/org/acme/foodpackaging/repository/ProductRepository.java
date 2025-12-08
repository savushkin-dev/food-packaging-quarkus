package org.acme.foodpackaging.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.acme.foodpackaging.domain.Product;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_PRODUCTS;

@ApplicationScoped
public class ProductRepository {

    @Inject
    @ConfigProperty(name = "db.url")
    String dbUrl;

    public Map<String, Product> loadProducts() {
        Map<String, Product> map = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(LOAD_PRODUCTS)) {

            while (rs.next()) {
                Product p = new Product(
                        rs.getString("SNM"),
                        rs.getString("KMC"),
                        rs.getString("KRKMC"),
                        rs.getString("EAN13"),
                        rs.getString("GRF"),
                        rs.getString("TGLAZ"),
                        rs.getString("TMASS"),
                        rs.getString("TFBF")
                );

                map.put(rs.getString("KMC"), p);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load products", e);
        }

        return map;
    }
}
