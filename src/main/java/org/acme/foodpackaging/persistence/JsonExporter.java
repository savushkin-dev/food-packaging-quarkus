package org.acme.foodpackaging.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.io.File;
import java.io.IOException;

public class JsonExporter {
    private final ObjectMapper objectMapper;

    public JsonExporter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Сохраняет расписание в JSON-файл.
     *
     * @param schedule решение планировщика
     * @param filePath путь к файлу (например, "output/schedule.json")
     * @return созданный файл
     * @throws IOException если не удалось сохранить
     */
    public File exportToFile(PackagingSchedule schedule, String filePath) throws IOException {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule is null, nothing to save.");
        }
        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        objectMapper.writeValue(file, schedule);
        return file;
    }

    /**
     * Возвращает JSON-представление расписания в виде строки.
     *
     * @param schedule решение планировщика
     * @return JSON-строка
     * @throws JsonProcessingException если сериализация не удалась
     */
    public String exportToString(PackagingSchedule schedule) throws JsonProcessingException {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule is null, nothing to export.");
        }
        return objectMapper.writeValueAsString(schedule);
    }
}
