package org.acme.foodpackaging.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LINES_WITH_NAME;

@ApplicationScoped
public class LineRepository {

    @Inject
    @ConfigProperty(name = "db.url")
    String dbUrl;

    public Map<String, String> loadLines() {
        Map<String, String> lines = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(LOAD_LINES_WITH_NAME)) {

            while (rs.next()) {
                lines.put(rs.getString("KRC"), rs.getString("SNM"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load lines from DB", e);
        }
        return lines;
    }
}
