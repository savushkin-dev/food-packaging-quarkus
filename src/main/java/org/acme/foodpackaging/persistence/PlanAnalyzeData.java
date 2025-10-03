package org.acme.foodpackaging.persistence;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LINE_WORK_FACT;

public class PlanAnalyzeData {

    public record Key(String productId, String np) {}

    public static class FactData {
        LocalDateTime startDate;
        LocalDateTime endDate;
        String lineId;
        String productId;
        String np;
        Duration duration;

        public void updateEnd(LocalDateTime newEnd) {
            if (endDate == null || newEnd.isAfter(endDate)) {
                this.endDate = newEnd;
                this.duration = Duration.between(startDate, endDate);
            }
        }
    }

    private Map<Key, FactData> factedMap;

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public PlanAnalyzeData() {
        Config config = ConfigProvider.getConfig();
        jdbcUrl = config.getValue("plan.db.url", String.class);
        user = config.getValue("plan.db.user", String.class);
        password = config.getValue("plan.db.password", String.class);
    }

    public Map<Key, FactData> getAggregatedMap(String date) {
        if (factedMap == null) {
            factedMap = readDataFromPostGres(date);
        }
        return factedMap;
    }

    private Map<Key, FactData> readDataFromPostGres(String date) {

        Map<Key, FactData> result = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = connection.prepareStatement(LOAD_LINE_WORK_FACT)) {

            LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(date + "T23:59:59");

            ps.setObject(1, start);
            ps.setObject(2, end);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime datetime = rs.getTimestamp("datetime1").toLocalDateTime();
                    String lineId = rs.getString("wc");
                    String product_id = rs.getString("producttype");
                    String np = rs.getString("batch");

                    Key key = new Key(product_id, np);
                    result.compute(key, (k, agg) -> {
                        if (agg == null) {
                            FactData newAgg = new FactData();
                            newAgg.startDate = datetime;
                            newAgg.endDate = datetime;
                            newAgg.lineId = lineId;
                            newAgg.productId = product_id;
                            newAgg.np = np;
                            newAgg.duration = Duration.ZERO;
                            return newAgg;
                        } else {
                            agg.updateEnd(datetime);
                            return agg;
                        }
                    });
                }
            }

        } catch (Exception e) {
            System.err.println("Failed load data for plan analyzing: " + e.getMessage());
        }

        return result;
    }
    }

