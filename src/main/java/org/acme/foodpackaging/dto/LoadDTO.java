package org.acme.foodpackaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class LoadDTO {

    private boolean findSolvedInDb;

    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime idealEndDateTime;

    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime maxEndDateTime;

    @Getter
    @JsonDeserialize(using = LineStartTimesDeserializer.class)
    private Map<String, LocalTime> lineStartTimes;

    public LoadDTO() {}

    public Boolean getFindSolvedInDb() { return findSolvedInDb; }
    public void setFindSolvedInDb(Boolean findSolvedInDb) { this.findSolvedInDb = findSolvedInDb; }

    public Map<String, LocalDateTime> toLineStartDateTimeMap() {
        Map<String, LocalDateTime> result = new LinkedHashMap<>();
        for (Map.Entry<String, LocalTime> entry : lineStartTimes.entrySet()) {
            result.put(entry.getKey(), LocalDateTime.of(startDate, entry.getValue()));
        }
        return result;
    }
}


