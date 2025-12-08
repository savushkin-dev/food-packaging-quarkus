package org.acme.foodpackaging.repository;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.CleaningRule;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_CLEANING_RULES;

@ApplicationScoped
public class CleaningRuleRepository {

    @Inject
    @ConfigProperty(name = "db.url")
    String dbUrl;

    public List<CleaningRule> loadRules() {
        List<CleaningRule> rules = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(LOAD_CLEANING_RULES)) {

            while (rs.next()) {
                rules.add(new CleaningRule(
                        rs.getString("NPAR"),
                        rs.getString("FROM_VALUE"),
                        rs.getString("TO_VALUE"),
                        rs.getInt("DUR")
                ));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load cleaning rules", e);
        }

        return rules;
    }
}
