package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.plrlc.EquipmentPeriodDto;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.lines.PlrLcRepository;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class LineActivitySyncService {

    private final LineRepository lineRepository;
    private final PlrLcRepository plrLcRepository;

    public void syncLines(
            PackagingSchedule schedule,
            LocalDate from,
            LocalDate to) {

        Set<String> activeLineIds = plrLcRepository.loadEquipmentPeriods()
                .stream()
                .filter(period -> isActiveInPeriod(period, from, to))
                .map(EquipmentPeriodDto::lineId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());

        syncScheduleLines(schedule, activeLineIds);
    }

    private void syncScheduleLines(
            PackagingSchedule schedule,
            Set<String> activeLineIds) {

        schedule.getLines().removeIf(line ->
                !activeLineIds.contains(line.getId()));

        Set<String> existingLineIds = schedule.getLines()
                .stream()
                .map(Line::getId)
                .collect(Collectors.toSet());

        activeLineIds.stream()
                .filter(id -> !existingLineIds.contains(id))
                .forEach(id -> addLineToSchedule(schedule, id));
    }

    private void addLineToSchedule(
            PackagingSchedule schedule, String lineId) {

        lineRepository.findLineInfo(lineId)
                .ifPresent(entity -> {

                    Line line = new Line();
                    line.setId(entity.getLineId().trim());
                    line.setName(entity.getSnm());
                    line.setDeletedLine(true);

                    schedule.getLines().add(line);
                });
    }

    private boolean isActiveInPeriod(
            EquipmentPeriodDto period,
            LocalDate from,
            LocalDate to) {

        if (period.begin() == null) {
            return false;
        }

        if (period.end() == null) {
            return true;
        }

        return !period.end().isBefore(from)
                && !period.begin().isAfter(to);
    }
}
