package org.acme.foodpackaging.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
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

}
