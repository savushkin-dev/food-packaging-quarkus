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
    private LocalDate planningDate;
    private LocalDateTime minStartDateTime;
    private LocalDateTime idealEndDateTime;
    private LocalDateTime maxEndDateTime;

    public WorkCalendar() {
    }

    public WorkCalendar(LocalDate fromDate, LocalDate toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public WorkCalendar(LocalDate currentDate){
        this.fromDate = currentDate.minusDays(2);
        this.toDate = currentDate.plusDays(7);
        this.planningDate = currentDate;
        this.minStartDateTime = currentDate.atStartOfDay();
    }

    @Override
    public String toString() {
        return fromDate + " - " + toDate;
    }

}
