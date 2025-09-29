package org.acme.foodpackaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class LoadDTO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime idealEndDateTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime maxEndDateTime;

    @JsonFormat(pattern = "HH:mm")
    private Map<String, LocalTime> lineStartTimes;

    public LoadDTO() {}

    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    public LocalDateTime getIdealEndDateTime() { return idealEndDateTime; }
    public LocalDateTime getMaxEndDateTime() { return maxEndDateTime; }

    public Map<String, LocalTime> getLineStartTimes() { return lineStartTimes; }

    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public void setIdealEndDateTime(LocalDateTime idealEndDateTime) { this.idealEndDateTime = idealEndDateTime; }
    public void setMaxEndDateTime(LocalDateTime maxEndDateTime) { this.maxEndDateTime = maxEndDateTime; }

    public void setLineStartTimes(Map<String, LocalTime> lineStartTimes) { this.lineStartTimes = lineStartTimes; }

    public Map<String, LocalDateTime> toLineStartDateTimeMap() {
        Map<String, LocalDateTime> result = new HashMap<>();
        for (Map.Entry<String, LocalTime> entry : lineStartTimes.entrySet()) {
            result.put(entry.getKey(), LocalDateTime.of(startDate, entry.getValue()));
        }
        return result;
    }

}


