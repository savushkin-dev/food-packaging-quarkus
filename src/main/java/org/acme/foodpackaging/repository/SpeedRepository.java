package org.acme.foodpackaging.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.math3.util.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.*;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LINES_SPEEDS;

@ApplicationScoped
public class SpeedRepository {

    @Inject
    @ConfigProperty(name = "db.url")
    String dbUrl;
    /**
     * Возвращает готовую структуру line -> (type -> speed)
     */
    public Map<String, Map<String, Integer>> createSpeedMap() {
        Map<Pair<String, String>, Integer> rawSpeeds = loadSpeeds();
        return groupSpeeds(rawSpeeds);
    }
    /**
     * Достаёт скорости: (line, type) -> speed
     */
    private Map<Pair<String, String>, Integer> loadSpeeds() {
        Map<Pair<String, String>, Integer> result = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(LOAD_LINES_SPEEDS)) {

            while (rs.next()) {
                String line = rs.getString("KRC");
                String type = rs.getString("GRF");
                int speed = rs.getInt("PROD");

                result.put(new Pair<>(line, type), speed);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load speeds from DB", e);
        }

        return result;
    }
    /**
     * Группировка по линиям + заполнение нулями отсувующих
     */
    private Map<String, Map<String, Integer>> groupSpeeds(Map<Pair<String, String>, Integer> rawSpeeds) {

        Map<String, Map<String, Integer>> finalMap = new HashMap<>();
        Set<String> allTypes = new HashSet<>();
        // Собирает все типы продуктов
        for (Pair<String, String> key : rawSpeeds.keySet()) {
            allTypes.add(key.getSecond());
        }
        // Заполняет скорости
        for (var entry : rawSpeeds.entrySet()) {
            Pair<String, String> pair = entry.getKey();
            String line = pair.getFirst();
            String type = pair.getSecond();
            Integer speed = entry.getValue();

            finalMap.computeIfAbsent(line, l -> {
                Map<String, Integer> map = new HashMap<>();
                for (String t : allTypes) {
                    map.put(t, 0);
                }
                return map;
            });

            finalMap.get(line).put(type, speed);
        }

        for (var typeMap : finalMap.values()) {
            for (String type : allTypes) {
                typeMap.putIfAbsent(type, 0);
            }
        }
        return finalMap;
    }
}
