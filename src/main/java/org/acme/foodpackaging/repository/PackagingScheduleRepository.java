package org.acme.foodpackaging.repository;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.acme.foodpackaging.domain.PackagingSchedule;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PackagingScheduleRepository {

    private final ConcurrentHashMap<String, SessionPackagingSchedule> sessionSolutions = new ConcurrentHashMap<>();

    public PackagingSchedule readForSession(String sessionId) {
        SessionPackagingSchedule session = sessionSolutions.get(sessionId);
        if (session != null) {
            session.updateTimestamp();
            return session.getSchedule();
        }
        return null;
    }

    public void writeForSession(String sessionId, PackagingSchedule schedule) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionSolutions.put(sessionId, new SessionPackagingSchedule(schedule));
        }
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionSolutions.remove(sessionId);
        }
    }

    @Scheduled(every = "24h")
    public void cleanupOldSessions() {
        long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000; // 24 часа назад
        sessionSolutions.entrySet().removeIf(entry -> entry.getValue().getLastUpdated() < cutoff);
    }

    @Getter
    private static class SessionPackagingSchedule {

        private final PackagingSchedule schedule;
        private volatile long lastUpdated;

        private SessionPackagingSchedule(PackagingSchedule schedule) {
            this.schedule = schedule;
            this.lastUpdated = System.currentTimeMillis();
        }

        private void updateTimestamp() {
            this.lastUpdated = System.currentTimeMillis();
        }
    }
}
