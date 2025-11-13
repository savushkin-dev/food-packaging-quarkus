package org.acme.foodpackaging.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class PackagingScheduleRepository {

    private final AtomicReference<PackagingSchedule> defaultSolution = new AtomicReference<>();
    private final ConcurrentHashMap<String, PackagingSchedule> sessionSolutions = new ConcurrentHashMap<>();

    public PackagingSchedule read() {
        return defaultSolution.get();
    }

    public PackagingSchedule readForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return defaultSolution.get();
        }
//        return sessionSolutions.get(sessionId);
        return sessionSolutions.getOrDefault(sessionId, defaultSolution.get());
    }

    public void write(PackagingSchedule schedule) {
        defaultSolution.set(schedule);
    }

    public void writeForSession(String sessionId, PackagingSchedule schedule) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionSolutions.put(sessionId, schedule);
        }
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionSolutions.remove(sessionId);
        }
    }
}