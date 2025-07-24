package org.acme.foodpackaging.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ProductionLine {
    private final LocalDate startDate;
    private final Map<Integer, LocalDateTime> lineStarts; // Номер линии → дата и время

    public ProductionLine(String startDateStr,
                          String startLine1, String startLine2, String startLine3,
                          String startLine4, String startLine5, String startLine6) {
      
        this.startDate = LocalDate.parse(startDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        this.lineStarts = new HashMap<>();

        // Добавляем время для каждой линии (если не указано — ставим 08:00)
        addLineTime(1, startLine1);
        addLineTime(2, startLine2);
        addLineTime(3, startLine3);
        addLineTime(4, startLine4);
        addLineTime(5, startLine5);
        addLineTime(6, startLine6);
    }

    private void addLineTime(int lineNumber, String timeStr) {
        LocalTime time;

        if (timeStr == null || timeStr.isEmpty()) {
            time = LocalTime.of(8, 0); // По умолчанию 08:00
        } else {
            try {
                time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception e) {
                System.err.println("Ошибка парсинга времени для линии " + lineNumber);
                time = LocalTime.of(8, 0);
            }
        }

        LocalDateTime dateTime = LocalDateTime.of(startDate, time);
        lineStarts.put(lineNumber, dateTime);
    }

    public Map<Integer, LocalDateTime> getLineStarts() {
        return lineStarts;
    }
}
