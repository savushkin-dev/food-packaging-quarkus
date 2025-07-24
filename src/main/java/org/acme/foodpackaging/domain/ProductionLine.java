package org.acme.foodpackaging.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ProductionLine {

    private final Map<Integer, LocalDateTime> lineStarts; // Номер линии → дата и время

    public ProductionLine(String startDateStr,
                          String startLine1, String startLine2, String startLine3,
                          String startLine4, String startLine5, String startLine6) {
        this.lineStarts = new HashMap<>();
        lineStarts.put(1, parseDateTime(startDateStr,startLine1));
        lineStarts.put(2, parseDateTime(startDateStr,startLine2));
        lineStarts.put(3, parseDateTime(startDateStr,startLine3));
        lineStarts.put(4, parseDateTime(startDateStr,startLine4));
        lineStarts.put(5, parseDateTime(startDateStr,startLine5));
        lineStarts.put(6, parseDateTime(startDateStr,startLine6));
    }

    public static LocalDateTime parseDateTime(String dateStr, String timeStr) {
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        return LocalDateTime.of(date, time);
    }

    public Map<Integer, LocalDateTime> getLineStarts() {
        return lineStarts;
    }
}
