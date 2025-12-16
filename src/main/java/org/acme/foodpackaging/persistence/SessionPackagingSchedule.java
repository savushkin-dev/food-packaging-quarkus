package org.acme.foodpackaging.persistence;

import lombok.Getter;
import org.acme.foodpackaging.domain.PackagingSchedule;

@Getter
public class SessionPackagingSchedule {

    private final PackagingSchedule schedule;
    private volatile long lastUpdated;

    public SessionPackagingSchedule(PackagingSchedule schedule) {
        this.schedule = schedule;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }
}
