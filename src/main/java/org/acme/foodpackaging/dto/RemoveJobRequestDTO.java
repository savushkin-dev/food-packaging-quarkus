package org.acme.foodpackaging.dto;

public class RemoveJobRequestDTO {
    private String lineId;
    private int removeIndex;

    public String getLineId() { return lineId; }
    public int getRemoveIndex() { return removeIndex; }
}
