package org.acme.foodpackaging.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class LineStartTimesDeserializer extends JsonDeserializer<Map<String, LocalTime>> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, LocalTime> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        Object value = p.readValueAs(Object.class);

        if (value instanceof Map<?, ?> rawMap) {
            Map<String, LocalTime> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                result.put(String.valueOf(e.getKey()), LocalTime.parse(String.valueOf(e.getValue())));
            }
            return result;
        }

        if (value instanceof String jsonString) {
            Map<String, String> temp = mapper.readValue(
                    jsonString,
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
            );
            Map<String, LocalTime> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : temp.entrySet()) {
                result.put(e.getKey(), LocalTime.parse(e.getValue()));
            }
            return result;
        }
        return Map.of();
    }
}
