package org.acme.foodpackaging.domain;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.time.Duration;
import java.util.*;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_CLEANING_RULES;

public class CleaningCalculator {

    private List<CleaningRule> rules;

    private String dbUrl;

    public CleaningCalculator() {

        Config config = ConfigProvider.getConfig();
        dbUrl = config.getValue("db.url", String.class);
        this.rules = loadCleaningRulesfromDB();
    }

    public CleaningCalculator(List<CleaningRule> rules) {

        this.rules = rules;
    }

    public int getCleaningTime(Product from, Product to) {
        List<Integer> times = new ArrayList<>();

        times.add(findDuration("1", from.getType(), to.getType()));       // тип продукта
        times.add(findDuration("2", from.getGlaze(), to.getGlaze()));     // глазурь
        times.add(findDuration("3", from.getCurdMass(), to.getCurdMass())); // масса
        times.add(findDuration("4", from.getFilling(), to.getFilling()));   // наполнитель

        return times.stream()
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(0);
    }

    private boolean matches(String ruleValue, String actual) {
        if (ruleValue.isBlank()) return true;
        return ruleValue.equalsIgnoreCase(actual == null ? "" : actual);
    }

    private Integer findDuration(String parameter, String from, String to) {
        return rules.stream()
                .filter(r -> r.getParameter().equals(parameter))
                .filter(r -> matches(r.getFrom(), from) && matches(r.getTo(), to))
                .sorted((r1, r2) -> Integer.compare(
                        specificity(r2, from, to),
                        specificity(r1, from, to)
                ))
                .map(CleaningRule::getDuration)
                .findFirst()
                .orElse(null);
    }

    private int specificity(CleaningRule rule, String from, String to) {
        int score = 0;
        if (!rule.getFrom().isBlank() && rule.getFrom().equalsIgnoreCase(from)) score++;
        if (!rule.getTo().isBlank() && rule.getTo().equalsIgnoreCase(to)) score++;
        return score;
    }

    public void cleaningCalculate(List<Product> products) {

        for (Product current : products) {
            Map<Product, Duration> durations = new HashMap<>(products.size());
            for (Product previous : products) {
                Duration duration;
                if(current.getId().equals("MAINTENANCE")){
                    duration = Duration.ZERO;
                }
              else if(current.getId().equals(previous.getId())){
                    duration = Duration.ZERO;
                }
                else if(current.getType().equals(previous.getType())
                        && current.getGlaze().equals(previous.getGlaze())
                        && current.getCurdMass().equals(previous.getCurdMass())
                        && current.getFilling().equals(previous.getFilling())
                        && !current.getId().equals(previous.getId())){

                    duration = Duration.ofMinutes(10); // Если совпадает все кроме Id, значит требуется только смена упаковки
                }
                else {
                    duration = Duration.ofMinutes(getCleaningTime(previous, current));
                }
                durations.put(previous, duration);
            }
            current.setCleaningDurations(durations);
        }
    }

    private List<CleaningRule> loadCleaningRulesfromDB(){

         this.rules = new ArrayList<>();

        ResultSet resultSet = null;
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement()) {
            resultSet = statement.executeQuery(LOAD_CLEANING_RULES);

            while (resultSet.next()) {
                String parameter = resultSet.getString("NPAR");
                String from_value = resultSet.getString("FROM_VALUE");
                String to_value = resultSet.getString("TO_VALUE");
                int duration = resultSet.getInt("DUR");
                CleaningRule rule = new CleaningRule(parameter, from_value, to_value, duration);
                rules.add(rule);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to load cleaning rules from DB", e);
        }
        return rules;
    }
}
