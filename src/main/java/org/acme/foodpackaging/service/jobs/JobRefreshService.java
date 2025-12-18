package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;

import java.util.Map;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;

@ApplicationScoped
public class JobRefreshService {

    @Inject
    JobRepository jobRepository;

    public void applySelection(Map<Integer, Boolean> selection) {
        for (Map.Entry<Integer, Boolean> entry : selection.entrySet()) {
            Integer snpz = entry.getKey();
            boolean enabled = entry.getValue();

            if (enabled) {
                if (!jobRepository.getJobIdMap().containsKey(snpz)) {
                    DbJobRow row = jobRepository.getDbJobRowMap().get(snpz);
                    if (row != null) {
                        Job job = jobRepository.createJobById(row.snpz().intValueExact());

                        jobRepository.getJobs().add(job);
                        jobRepository.getJobIdMap().put(snpz, job);}
                }
            } else {
                Job job = jobRepository.getJobIdMap().remove(snpz);
                if (job != null) {
                    jobRepository.getJobs().remove(job);

                    Line line = job.getLine();
                    if (line != null) {
                        line.getJobs().remove(job);
                        job.setLine(null);

                        fixLineJobs(line);
                        if(line.getFirstUnpinnedIndex()>line.getJobs().size()) {
                            line.setFirstUnpinnedIndex(line.getJobs().size());
                            }
                        }
                    }
                }
            }
        }

    private void rebuildId() {
        jobRepository.getJobIdMap().clear();
        for (Job j : jobRepository.getJobs()) {
            jobRepository.getJobIdMap().put(j.getSnpz(), j);
        }
    }
}
