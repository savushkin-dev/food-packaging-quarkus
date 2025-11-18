package org.acme.foodpackaging.dto;

public class RemoveJobRequestDTO {
    private String lineId;
    private int index;

    public String getLineId() { return lineId; }
    public int getIndex() { return index; }
}
