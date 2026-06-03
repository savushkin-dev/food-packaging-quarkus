package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.plrlc.EquipmentPeriodDto;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.lines.PlrLcRepository;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class DeletedLineLoader {

    private final LineRepository lineRepository;
    private final PlrLcRepository plrLcRepository;

    public void loadDeletedLines(
            PackagingSchedule schedule,
            LocalDate from,
            LocalDate to) {

        Set<String> existingLineIds = schedule.getLines()
                .stream()
                .map(Line::getId)
                .collect(Collectors.toSet());

        plrLcRepository.loadEquipmentPeriods()
                .stream()
                .filter(period -> isActiveInPeriod(period, from, to))
                .filter(period -> !existingLineIds.contains(period.lineId()))
                .forEach(period -> addDeletedLine(
                        period.lineId(),
                        schedule,
                        existingLineIds));
    }

    private void addDeletedLine(
            String lineId,
            PackagingSchedule schedule,
            Set<String> existingLineIds) {

        lineRepository.findLineInfo(lineId)
                .ifPresent(entity -> {

                    Line line = new Line();
                    line.setId(entity.getLineId().trim());
                    line.setName(entity.getSnm());
                    line.setDeletedLine(true);

                    schedule.getLines().add(line);
                    existingLineIds.add(line.getId());
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

