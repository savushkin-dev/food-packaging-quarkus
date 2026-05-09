package builder;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleTestBuilder {

    private final PackagingSchedule schedule = new PackagingSchedule();

    private ScheduleTestBuilder() {}

    public static ScheduleTestBuilder aSchedule() {
        return new ScheduleTestBuilder();
    }

    public ScheduleTestBuilder withWorkCalendar(LocalDate date, LocalDateTime minStart) {
        WorkCalendar workCalendar = new WorkCalendar(date);
        workCalendar.setMinStartDateTime(minStart);
        schedule.setWorkCalendar(workCalendar);
        return this;
    }

   public ScheduleTestBuilder withLines(Line... lines) {
        schedule.setLines(new ArrayList<>(List.of(lines)));
        return this;
    }

    public ScheduleTestBuilder withEmptyJobs() {
        schedule.setJobs(new ArrayList<>());
        return this;
    }

    public ScheduleTestBuilder withJobs(Job... jobs) {
        schedule.setJobs(new ArrayList<>(List.of(jobs)));
        return this;
    }

    public ScheduleTestBuilder withProductsList(List<Product> products) {
        schedule.setProducts(products);
        return this;
    }

    public ScheduleTestBuilder withEmptyJobMap() {
        schedule.setAllJobsById(new HashMap<>());
        return this;
    }
 
    public ScheduleTestBuilder withSpeed(String lineId, String productType, int speed) {
        SpeedCacheUtils.init(Map.of(
                lineId,
                Map.of(productType, Pair.of(speed, speed))
        ));
        return this;
    }

    public PackagingSchedule build() {
        return schedule;
    }
}

