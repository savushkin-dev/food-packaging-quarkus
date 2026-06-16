package fixtures;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.WorkCalendar;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;

public final class SolutionFixtures {

    private SolutionFixtures(){
    }

    public static void  initSpeedCache() {
        Map<String, Pair<Integer, Integer>> line1Speeds = new HashMap<>();
        line1Speeds.put("TYPE_A", Pair.of(100, 50));
        line1Speeds.put("TYPE_B", Pair.of(200, 80));

        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();
        speeds.put("L007", line1Speeds);

        SpeedCacheUtils.init(speeds);

        CleaningDurationUtils.init(Map.of("L007", 45));
    }

    public static PackagingSchedule solutionWithLines() {
        PackagingSchedule solution = new PackagingSchedule();
        initSpeedCache();

        List<Line> lines = new ArrayList<>(
                List.of(new Line(), new Line("L2", "line2"))
        );

        Pair<Job, Job> jobsForLine = JobFixtures.jobsWithCleanings();

        Job j1 = jobsForLine.getLeft();
        Job j2 = jobsForLine.getRight();

        Line line007 = j1.getLine();
        line007.setJobs(new ArrayList<>(List.of(j1, j2)));
        fixLineJobs(line007);
        
        LocalDate startDate = line007.getStartDateTime().toLocalDate();

        solution.setWorkCalendar(new WorkCalendar(startDate));
        solution.setJobs(List.of(j1, j2));
        lines.add(line007);
        solution.setLines(lines);

        return solution;
    }
}
