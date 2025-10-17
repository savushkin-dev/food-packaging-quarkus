package org.acme.foodpackaging.dto;

public class MoveJobsRequestDTO {
    private String fromLineId;
    private String toLineId;
    private int fromIndex;
    private int count;
    private int insertIndex;

    public String getFromLineId() { return fromLineId; }
    public void setFromLineId(String fromLineId) { this.fromLineId = fromLineId; }
    public String getToLineId() { return toLineId; }
    public void setToLineId(String toLineId) { this.toLineId = toLineId; }
    public int getFromIndex() { return fromIndex; }
    public void setFromIndex(int fromIndex) { this.fromIndex = fromIndex; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public int getInsertIndex() { return insertIndex; }
    public void setInsertIndex(int insertIndex) { this.insertIndex = insertIndex; }
}
