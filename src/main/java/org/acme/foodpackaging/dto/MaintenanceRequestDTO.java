package org.acme.foodpackaging.dto;

public class MaintenanceRequestDTO {
    private String lineId;
    private String name;
    private int insertIndex;
    private int durationMinutes;


    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public int getInsertIndex() {
        return insertIndex;
    }

    public void setInsertIndex(int insertIndex) {
        this.insertIndex = insertIndex;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getName() { return name; }

    public void setName( String name) { this.name = name; }
}
