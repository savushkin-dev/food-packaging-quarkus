package org.acme.foodpackaging.dto;

public class PinRequest {
    private String lineId;
    private Integer pinCount;
    private Boolean pinAll;

    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }

    public Integer getPinCount() { return pinCount; }
    public void setPinCount(Integer pinCount) { this.pinCount = pinCount; }

    public Boolean getPinAll() { return pinAll; }
    public void setPinAll(Boolean pinAll) { this.pinAll = pinAll; }
}
