package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.dto.LineProductionDto;
import org.acme.foodpackaging.dto.ShiftProductionDto;
import org.acme.foodpackaging.repository.PmLogRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LineService {

    private final LoadDataService loadDataService;
    private final PmLogRepository pmLogRepository;

    private static final int MASS_SCALE = 2;

    public List<Line> getLines() {
        return loadDataService.getLines().entrySet().stream()
                .sorted(lineNameComparator())
                .map(e -> new Line(e.getKey(), e.getValue()))
                .toList();
    }

    private Comparator<Map.Entry<String, String>> lineNameComparator() {
        return Comparator
                .comparingInt((Map.Entry<String, String> e) -> extractLineNumber(e.getValue()))
                .thenComparing(Map.Entry::getValue);
    }

    private int extractLineNumber(String name) {
        Matcher matcher = Pattern.compile("№\\s*(\\d+)").matcher(name);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Integer.MAX_VALUE;
    }

    public void initLineStartEnd(PackagingSchedule solution) {

        if (solution.getLines() == null) {
            return;
        }

        LocalDateTime defaultStart = solution.getWorkCalendar()
                .getPlanningDate()
                .atStartOfDay();

        for (Line line : solution.getLines()) {

            List<Job> jobs = line.getJobs();

            if (jobs == null || jobs.isEmpty()) {
                line.setStartDateTime(defaultStart);
                line.setMaxEndTime(defaultStart.plusHours(24));
                continue;
            }

            jobs.sort(
                    Comparator.comparing(
                            Job::getStartProductionDateTime,
                            Comparator.nullsLast(Comparator.naturalOrder())));

            Job firstJob = jobs.getFirst();
            Job lastJob = jobs.getLast();

            line.setStartDateTime(firstJob.getStartProductionDateTime());

            fixLineJobs(line);
            fixPinnedJobs(line);

            if (lastJob.getEndDateTime() != null) {
                line.setMaxEndTime(lastJob.getEndDateTime().plusHours(24));
            }
        }
    }

    public void setMaxEndDateTimeByLastJob(PackagingSchedule solution) {

        if (solution.getLines() == null)
            return;
        for (Line line : solution.getLines()) {

            if (line.getStartDateTime() == null) {
                line.setStartDateTime(solution.getWorkCalendar().getPlanningDate().atStartOfDay());
            }

            if (line.getJobs() == null || line.getJobs().isEmpty()) {
                line.setMaxEndTime(line.getStartDateTime().plusHours(24));
                continue;
            }

            line.setMaxEndTime(line.getJobs().getLast().getEndDateTime().plusHours(24));
        }
    }

    // ============================================================
    // LineProduction
    // ============================================================

    public Map<String, LineProductionDto> calculateLineProductions(List<Line> lines, LocalDate selectedDate) {
        ShiftWindow firstShiftWindow = ShiftWindow.forDate(selectedDate, 1);
        ShiftWindow secondShiftWindow = ShiftWindow.forDate(selectedDate, 2);

        Map<String, LineProductionDto> result = LinkedHashMap.newLinkedHashMap(lines.size());
        for (Line line : lines) {
            result.put(String.valueOf(line.getId()), buildLineProduction(line, firstShiftWindow, secondShiftWindow));
        }
        return result;
    }

    private LineProductionDto buildLineProduction(Line line, ShiftWindow firstShiftWindow,
            ShiftWindow secondShiftWindow) {
        String lineKey = String.valueOf(line.getId());

        if (line.getJobs() == null || line.getJobs().isEmpty()) {
            ShiftProductionDto empty = new ShiftProductionDto(0.0, Map.of());
            return new LineProductionDto(lineKey, line.getName(), empty, empty, 0.0);
        }

        ShiftProductionDto shift1 = buildShiftProduction(line.getJobs(), firstShiftWindow);
        ShiftProductionDto shift2 = buildShiftProduction(line.getJobs(), secondShiftWindow);
        double totalMass = round(shift1.massa() + shift2.massa());

        return new LineProductionDto(lineKey, line.getName(), shift1, shift2, totalMass);
    }

    private ShiftProductionDto buildShiftProduction(List<Job> jobs, ShiftWindow window) {
        Map<String, Double> snpz = new LinkedHashMap<>();
        double shiftMass = 0.0;

        for (Job job : jobs) {
            double mass = calculateJobMass(job, window);
            if (mass <= 0) {
                continue;
            }

            double roundedMass = round(mass);
            shiftMass += roundedMass;
            snpz.put(String.valueOf(job.getId()), roundedMass);
        }

        return new ShiftProductionDto(round(shiftMass), snpz);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(MASS_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double calculateJobMass(Job job, ShiftWindow window) {
        LocalDateTime start = job.getCameraStart();
        LocalDateTime end = job.getCameraEnd();

        if (start == null || end == null || !window.overlaps(start, end)) {
            return 0.0;
        }

        return window.fullyContains(start, end)
                ? job.getMass()
                : calculatePartialMass(job, window);
    }

    private double calculatePartialMass(Job job, ShiftWindow window) {
        String idBatch = job.getIdBatch();
        if (idBatch == null) {
            return 0.0;
        }

        Double successRate = switch (window.crossingType(job.getCameraStart())) {
            case CROSSES_START -> pmLogRepository.getSuccessRateFromStart(idBatch, window.start());
            case CROSSES_END -> pmLogRepository.getSuccessRateUntilEnd(idBatch, window.end());
        };

        return successRate == null ? 0.0 : job.getMass() * successRate;
    }
}