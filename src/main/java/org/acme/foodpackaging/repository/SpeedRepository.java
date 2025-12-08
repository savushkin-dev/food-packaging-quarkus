package org.acme.foodpackaging.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.math3.util.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LINES_SPEEDS;

@ApplicationScoped
public class SpeedRepository {

    @Inject
    @ConfigProperty(name = "db.url")
    String dbUrl;

    public Map<Pair<String, String>, Integer> loadSpeeds() {
        Map<Pair<String, String>, Integer> map = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(LOAD_LINES_SPEEDS)) {

            while (rs.next()) {
                String line = rs.getString("KRC");
                String type = rs.getString("GRF");
                int speed = rs.getInt("PROD");

                map.put(new Pair<>(line, type), speed);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load speeds from DB", e);
        }

        return map;
    }
}
