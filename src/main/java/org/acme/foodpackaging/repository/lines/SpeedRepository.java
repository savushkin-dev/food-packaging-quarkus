package org.acme.foodpackaging.repository.lines;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.math3.util.Pair;
import org.acme.foodpackaging.entity.lines.LineSpeedEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SpeedRepository implements PanacheRepository<LineSpeedEntity> {
    /**
     * Загружает все строки из БД в Map<Pair<line, type>, speed>
     */
    public Map<Pair<String, String>, Integer> loadSpeeds() {
        Map<Pair<String, String>, Integer> result = new HashMap<>();

        List<LineSpeedEntity> lineSpeeds = find("speed IS NOT NULL").list();

        for (LineSpeedEntity line : lineSpeeds) {
            Pair<String, String> key = new Pair<>(line.getLine(), line.getType());

            result.putIfAbsent(key, line.getSpeed());
        }

        return result;
    }
    /**
     * Группирует в Map<line, Map<type, speed>>
     */
    public Map<String, Map<String, Integer>> createSpeedMap() {
        Map<Pair<String, String>, Integer> rawSpeeds = loadSpeeds();
        Map<String, Map<String, Integer>> speedMap = new HashMap<>();

        var allTypes = rawSpeeds.keySet().stream().map(Pair::getSecond).toList();

        rawSpeeds.forEach((pair, speed) -> {
            speedMap.computeIfAbsent(pair.getFirst(), l -> {
                Map<String, Integer> map = new HashMap<>();
                allTypes.forEach(t -> map.put(t, 0));
                return map;
            });

            speedMap.get(pair.getFirst()).put(pair.getSecond(), speed);
        });

        // добавляет пропущенные типы
        speedMap.values().forEach(typeMap -> allTypes.forEach(t -> typeMap.putIfAbsent(t, 0)));

        return speedMap;
    }
}
