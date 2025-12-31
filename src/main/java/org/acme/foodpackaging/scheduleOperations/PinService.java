package org.acme.foodpackaging.scheduleOperations;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.dto.PinRequest;

import java.util.List;

@ApplicationScoped
public class PinService {
    /**
     * Закрепляет указанное количество заданий на линии.
     * Если count >= количества заданий на линии, закрепляются все задания.
     */
    public void pinJobs(Line line, Integer count) {
        if (count == null) {
            line.setFirstUnpinnedIndex(0);
        } else if (count <= 0) {
            line.setFirstUnpinnedIndex(0);
        } else {
            int safeCount = Math.min(count, line.getJobs().size());
            line.setFirstUnpinnedIndex(safeCount);
        }
    }
    /**
     * Закрепляет все задания на всех линиях.
     */
    public void pinAllLines(List<Line> lines) {
        for (Line line : lines) {
            line.setFirstUnpinnedIndex(line.getJobs().size());
        }
    }
    /**
     * Открепляет все задания на всех линиях.
     */
    public void unPinAllLines(List<Line> lines) {
        for (Line line : lines) {
            line.setFirstUnpinnedIndex(0);
        }
    }

    /**
     * Закрепляет/открепляет все задания на конкретной линии в зависимости от флагов.
     */
    public void pinLine(Line line, PinRequest request) {
        if (Boolean.TRUE.equals(request.getPinAll())) {
            line.setFirstUnpinnedIndex(line.getJobs().size());
        } else if (request.getPinCount() != null) {
            pinJobs(line, request.getPinCount());
        } else {
            line.setFirstUnpinnedIndex(0); // открепляет всю линию
        }
    }
}

