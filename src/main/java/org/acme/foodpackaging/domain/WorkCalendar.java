package org.acme.foodpackaging.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class WorkCalendar {

    private LocalDate fromDate; // Inclusive
    private LocalDate toDate; // Exclusive
    private LocalDateTime minStartDateTime;
    private LocalDateTime idealEndDateTime;
    private LocalDateTime maxEndDateTime;

    public WorkCalendar() {
    }

    public WorkCalendar(LocalDate fromDate, LocalDate toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public WorkCalendar(LocalDate fromDate, LocalDate toDate, LocalDateTime minStartDateTime,
                        LocalDateTime idealEndDateTime, LocalDateTime maxEndDateTime) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.minStartDateTime = minStartDateTime;
        this.idealEndDateTime = idealEndDateTime;
        this.maxEndDateTime = maxEndDateTime;
    }

    @Override
    public String toString() {
        return fromDate + " - " + toDate;
    }

    // ************************************************************************
    // Getters and setters
    // ************************************************************************

    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public void setMinStartDateTime(LocalDateTime minStartDateTime) { this.minStartDateTime = minStartDateTime; }

    public void setIdealEndDateTime(LocalDateTime idealEndDateTime) { this.idealEndDateTime = idealEndDateTime; }

    public void setMaxEndDateTime(LocalDateTime maxEndDateTime) { this.maxEndDateTime = maxEndDateTime; }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public LocalDateTime getMinStartDateTime() {
        return minStartDateTime;
    }

    public LocalDateTime getIdealEndDateTime() {
        return idealEndDateTime;
    }

    public LocalDateTime getMaxEndDateTime() { return maxEndDateTime; }
}
