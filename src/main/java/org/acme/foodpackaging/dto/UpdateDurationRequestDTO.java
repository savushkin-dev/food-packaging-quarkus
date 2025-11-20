package org.acme.foodpackaging.dto;

public class UpdateDurationRequestDTO {
    private String lineId;
    private int index;
    private int durationMinutes;

    public String getLineId() { return lineId; }

    public int getDurationMinutes() { return durationMinutes; }
    public int getIndex() { return index; }
}
