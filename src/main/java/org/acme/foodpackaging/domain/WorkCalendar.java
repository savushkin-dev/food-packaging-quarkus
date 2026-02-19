package org.acme.foodpackaging.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
public class WorkCalendar {

    private LocalDate fromDate;
    private LocalDate toDate;
    private LocalDateTime minStartDateTime;
    private LocalDateTime idealEndDateTime;
    private LocalDateTime maxEndDateTime;

    public WorkCalendar() {
    }

    public WorkCalendar(LocalDate fromDate, LocalDate toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public WorkCalendar(LocalDate fromDate){
        this.fromDate = fromDate.minusDays(1);
        this.toDate = fromDate.plusDays(10);
        this.minStartDateTime = fromDate.atStartOfDay();
    }

    @Override
    public String toString() {
        return fromDate + " - " + toDate;
    }

}
