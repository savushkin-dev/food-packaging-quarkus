package org.acme.foodpackaging.domain;

import lombok.Getter;

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
