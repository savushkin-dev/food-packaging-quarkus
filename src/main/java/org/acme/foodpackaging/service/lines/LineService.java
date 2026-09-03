package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.dto.response.lineservice.*;

import org.acme.foodpackaging.repository.PmLogRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public Map<String, Object> calculateLineProductions(List<Line> lines, LocalDateTime shiftStart) {
        ShiftWindow firstShiftWindow = ShiftWindow.forShiftStart(shiftStart, 1);
        ShiftWindow secondShiftWindow = ShiftWindow.forShiftStart(shiftStart, 2);

        Map<String, Object> result = LinkedHashMap.newLinkedHashMap(lines.size() + 1);

        double totalMassa1 = 0.0;
        double totalMassa2 = 0.0;

        for (Line line : lines) {
            LineProductionDto lineDto = buildLineProduction(line, firstShiftWindow, secondShiftWindow);
            result.put(String.valueOf(line.getId()), lineDto);
            totalMassa1 += lineDto.massa1();
            totalMassa2 += lineDto.massa2();
        }

        double totalMassa = round(totalMassa1 + totalMassa2);
        result.put("total", new TotalProductionDto("Итого", totalMassa, round(totalMassa1), round(totalMassa2)));

        return result;
    }

    private LineProductionDto buildLineProduction(Line line, ShiftWindow firstShiftWindow,
            ShiftWindow secondShiftWindow) {
        if (line.getJobs() == null || line.getJobs().isEmpty()) {
            return new LineProductionDto(line.getName(), 0.0, 0.0, 0.0, List.of(), List.of());
        }

        List<BatchProductionDto> shift1 = buildShiftBatches(line.getJobs(), firstShiftWindow);
        List<BatchProductionDto> shift2 = buildShiftBatches(line.getJobs(), secondShiftWindow);

        double massa1 = sumMass(shift1);
        double massa2 = sumMass(shift2);
        double totalMass = round(massa1 + massa2);

        return new LineProductionDto(line.getName(), totalMass, massa1, massa2, shift1, shift2);
    }

    private List<BatchProductionDto> buildShiftBatches(List<Job> jobs, ShiftWindow window) {
        List<BatchProductionDto> batches = new ArrayList<>();

        for (Job job : jobs) {
            double mass = calculateJobMass(job, window);
            if (mass <= 0) {
                continue;
            }

            batches.add(new BatchProductionDto(
                    job.getId(),
                    round(mass),
                    job.getNp(),
                    job.getCameraStart(),
                    job.getCameraEnd()));
        }

        batches.sort(Comparator.comparing(BatchProductionDto::dts, Comparator.nullsLast(Comparator.naturalOrder())));
        return batches;
    }

    private static double sumMass(List<BatchProductionDto> batches) {
        double sum = 0.0;
        for (BatchProductionDto batch : batches) {
            sum += batch.massa();
        }
        return round(sum);
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