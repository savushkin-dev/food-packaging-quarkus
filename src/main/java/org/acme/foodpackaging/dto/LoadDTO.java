package org.acme.foodpackaging.dto;

import org.acme.foodpackaging.domain.ProductionLine;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

public class LoadDTO {

    private String startDate;
    private String endDate;

    private String idealEndDateTime;
    private String maxEndDateTime;

    private String startLine1;
    private String startLine2;
    private String startLine3;
    private String startLine4;
    private String startLine5;
    private String startLine6;

    public LoadDTO() {}

    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }

    public LocalDateTime getIdealEndDateTime() {
        return ProductionLine.parseDateTime(endDate, idealEndDateTime);
    }
    public LocalDateTime getMaxEndDateTime() {
        return ProductionLine.parseDateTime(endDate, maxEndDateTime);
    }

    public String getMaxEndTime() { return maxEndDateTime; }

    public String getStartLine1() { return startLine1; }
    public String getStartLine2() { return startLine2; }
    public String getStartLine3() { return startLine3; }
    public String getStartLine4() { return startLine4; }
    public String getStartLine5() { return startLine5; }
    public String getStartLine6() { return startLine6; }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
    public void setIdealEndDateTime(String idealEndDateTime) {
        this.idealEndDateTime = idealEndDateTime;
    }
    public void setMaxEndDateTime(String maxEndDateTime) {
        this.maxEndDateTime = maxEndDateTime;
    }

    public void setStartLine1(String  startLine1) { this.startLine1 = startLine1; }
    public void setStartLine2(String  startLine2) { this.startLine2 = startLine2; }
    public void setStartLine3(String  startLine3) { this.startLine3 = startLine3; }
    public void setStartLine4(String  startLine4) { this.startLine4 = startLine4; }
    public void setStartLine5(String  startLine5) { this.startLine5 = startLine5; }
    public void setStartLine6(String  startLine6) { this.startLine6 = startLine6; }

    public Map<Integer, LocalDateTime> getLineStartsMap() {
        if (startDate == null) return Collections.emptyMap();

        return Collections.unmodifiableMap(
                new ProductionLine(
                        startDate, startLine1, startLine2, startLine3,
                        startLine4, startLine5, startLine6
                ).getLineStarts()
        );
    }
}
