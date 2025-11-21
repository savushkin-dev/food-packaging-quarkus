package org.acme.foodpackaging.domain;

public class SessionPackagingSchedule {

    private final PackagingSchedule schedule;
    private volatile long lastUpdated;

    public SessionPackagingSchedule(PackagingSchedule schedule) {
        this.schedule = schedule;
        this.lastUpdated = System.currentTimeMillis();
    }

    public PackagingSchedule getSchedule() {
        return schedule;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }
}
