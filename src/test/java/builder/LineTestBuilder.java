package builder;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LineTestBuilder {
    private final Line line;

    private LineTestBuilder(String id, LocalDateTime start) {
        this.line = new Line(id, "Line " + id, "op", start);
        this.line.setJobs(new ArrayList<>());
    }

    public static LineTestBuilder aLine(String id, LocalDateTime start) {
        return new LineTestBuilder(id, start);
    }

    public LineTestBuilder withJobs(Job... jobs) {
        line.setJobs(new ArrayList<>(List.of(jobs)));

        for (int i = 0; i < jobs.length; i++) {
            Job job = jobs[i];
            job.setLine(line);

            if (i > 0) {
                job.setPreviousJob(jobs[i - 1]);
            }
        }

        return this;
    }

    public LineTestBuilder autoLink() {
        List<Job> jobs = line.getJobs();
        for (int i = 1; i < jobs.size(); i++) {
            jobs.get(i).setPreviousJob(jobs.get(i - 1));
        }
        return this;
    }

    public Line build() {
        return line;
    }
}