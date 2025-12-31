package org.acme.foodpackaging.repository.lines;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.lines.PlrLines;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SpeedRepository implements PanacheRepository<PlrLines> {

    public static Map<String, Map<String, Integer>> createSpeedMap(
            Map<LineTypeKey, Integer> rawSpeeds) {

        Set<String> allTypes = rawSpeeds.keySet()
                .stream()
                .map(LineTypeKey::type)
                .collect(Collectors.toSet());

        Map<String, Map<String, Integer>> speedMap = new HashMap<>();

        rawSpeeds.forEach((key, speed) ->
                speedMap
                        .computeIfAbsent(key.line(), l -> new HashMap<>())
                        .put(key.type(), speed)
        );

        speedMap.values().forEach(typeMap ->
                allTypes.forEach(t -> typeMap.putIfAbsent(t, 0))
        );

        return speedMap;
    }

    public record LineTypeKey(String line, String type) {}
}

