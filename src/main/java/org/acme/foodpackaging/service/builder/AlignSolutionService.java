package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixPinnedJobs;

@ApplicationScoped
public class AlignSolutionService {

    private final MaintenanceJob maintenanceJob;

    public AlignSolutionService(MaintenanceJob maintenanceJob) {
        this.maintenanceJob = maintenanceJob;
    }

    public void alignByFactDuration(PackagingSchedule schedule) {
       removePackagingMaintenance(schedule);
       if(schedule.getLines()==null) return;
       for (Line line : schedule.getLines()) {
           List<Job> jobs = line.getJobs();
           if (jobs == null || jobs.isEmpty()) {
               continue;
           }
           fixDurationByFact(line);
           }
    }

    private void removePackagingMaintenance(PackagingSchedule schedule){
        List<Job> jobs = schedule.getJobs();
        if (schedule.getJobs() == null || schedule.getJobs().isEmpty()) {
            return;
        }
        jobs.removeIf(job -> job.isMaintenance() && job.getMaintenanceTypeId() == 7);
        for(Line line : schedule.getLines()){

            if (line == null || line.getJobs() == null || line.getJobs().isEmpty()) {
                continue;
            }
            List<Job> lineJobs = line.getJobs();

            lineJobs.removeIf(job -> job.isMaintenance() && job.getMaintenanceTypeId() == 7);
            fixLineJobs(line);
            fixPinnedJobs(line);
        }
    }

    private void  fixDurationByFact(Line line) {

        for (Job job : line.getJobs()) {
            Long factMinutes = calculateFactMinutes(job);
            if (factMinutes == null) {
                continue;
            }
            long planMinutes = calculatePlanMinutes(job);
            long diff = factMinutes - planMinutes;
            if(diff>0){
                job.setPlanEndDateTime(job.getEndDateTime());
                job.setEndDateTime(job.getPlanEndDateTime().plusMinutes(diff));
                job.setDelayDuration(Duration.ofMinutes(diff));
                job.setDuration(Duration.ofMinutes(factMinutes));
                job.setFinalDuration(true);
                fixLineJobs(line);
                fixPinnedJobs(line);
            }

        }
    }

    private long calculatePlanMinutes(Job job) {
        if (job.getStartProductionDateTime() == null
                || job.getEndDateTime() == null) {
            return 0;
        }

        return ceilMinutes(Duration.between(
                job.getStartProductionDateTime(),
                job.getEndDateTime()
        ));
    }

    private Long calculateFactMinutes(Job job) {
        if (job.getCameraStart() == null
                || job.getCameraEnd() == null) {
            return null;
        }

        return ceilMinutes(Duration.between(
                job.getCameraStart(),
                job.getCameraEnd()
        ));
    }

    private long ceilMinutes(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 0;
        }
        return (duration.toSeconds() + 59) / 60;
    }

    public void alignLineStartByFact(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            alignLineStartByFactForLine(schedule, line);
        }
    }

    private void alignLineStartByFactForLine(PackagingSchedule schedule, Line line) {
        List<Job> jobs = line.getJobs();
        if (jobs == null || jobs.isEmpty()) return;

        List<Job> factJobs = collectFactJobs(jobs);
        if (factJobs.isEmpty()) return;

        Map<String, List<Job>> chainsByProduct = buildLastChainsByProduct(factJobs);
        if (chainsByProduct.isEmpty()) return;

        // Возьмем последнюю цепочку последнего продукта по факту
        List<Job> factChain = chainsByProduct.get(factJobs.getLast().getProduct().getId());
        if ( factChain == null ||  factChain.isEmpty()) return;
        String productId = factChain.getLast().getProduct().getId();

        List<Job> planJobs = findPlanJobsByProduct(jobs, productId);
        if (planJobs.size() < 2) return;

        NpBounds bounds = getNpBounds(factChain);
        if (bounds.min() == null) return;

        PlanTarget target = selectTargetPlan(jobs, planJobs, bounds.min(), bounds.max());
        if (target == null) return;

        Job firstFact = factChain.getFirst();
        LocalDateTime factStart = firstFact.getCameraStart();
        LocalDateTime planStart = target.job().getStartCleaningDateTime();
        if (planStart == null) return;

        if (tryUpdateExistingAlignMaintenance(schedule, line, jobs, target.job(), factStart)) {
            return;
        }

        if (!planStart.isBefore(factStart)) return;

        long diffMinutes = ceilMinutes(Duration.between(planStart, factStart));
        Integer extraMinutes = null;
        if(target.job.getStartProductionDateTime()!=target.job.getStartCleaningDateTime()){
            long cleaningMinutes = ceilMinutes(Duration.between(planStart, target.job.getStartProductionDateTime()));
            diffMinutes -= cleaningMinutes;
            extraMinutes = (int) cleaningMinutes;
        }
        if (diffMinutes <= 0) return;

        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId(line.getId());
        request.setInsertIndex(target.index());
        request.setDurationMinutes((int) diffMinutes);
        request.setMaintenanceTypeId(8);
        request.setAlignExtraCleaning(extraMinutes);
        request.setMaintenanceNote("Выравнивание последней фактической цепочки продукта " + productId);

        maintenanceJob.addMaintenanceJob(schedule, request);
    }

    private List<Job> collectFactJobs(List<Job> jobs) {
        return jobs.stream()
                .filter(j -> !j.isMaintenance())
                .filter(j -> j.getProduct() != null)
                .filter(j -> j.getCameraStart() != null)
                .filter(j -> j.getCameraEnd() != null)
                .sorted(Comparator.comparing(Job::getCameraStart))
                .toList();
    }

    private Map<String, List<Job>> buildLastChainsByProduct(List<Job> factJobs) {
        Map<String, List<Job>> result = new LinkedHashMap<>();
        int i = factJobs.size() - 1;
        while (i >= 0) {
            i = processRunAndReturnNextIndex(i, result, factJobs);
        }
        return result;
    }

    private int processRunAndReturnNextIndex(int idx, Map<String, List<Job>> result, List<Job> factJobs) {
        String productId = factJobs.get(idx).getProduct().getId();
        if (result.containsKey(productId)) {
            return idx - 1;
        }
        int pos = idx;
        while (pos >= 0 && productId.equals(factJobs.get(pos).getProduct().getId())) {
            pos--;
        }
        int start = pos + 1;
        result.put(productId, new ArrayList<>(factJobs.subList(start, idx + 1)));
        return pos;
    }

    private List<Job> findPlanJobsByProduct(List<Job> jobs, String productId) {
        return jobs.stream()
                .filter(j -> !j.isMaintenance())
                .filter(j -> j.getProduct() != null)
                .filter(j -> productId.equals(j.getProduct().getId()))
                .toList();
    }

    private NpBounds getNpBounds(List<Job> chain) {
        Job minNpJob = chain.stream().min(Comparator.comparing(Job::getNp)).orElse(null);
        Job maxNpJob = chain.stream().max(Comparator.comparing(Job::getNp)).orElse(null);
        return new NpBounds(minNpJob, maxNpJob);
    }

    private PlanTarget selectTargetPlan(List<Job> jobs, List<Job> planJobs, Job minJob, Job maxJob) {
        Job planMin = planJobs.stream().filter(j -> j.getId().equals(minJob.getId())).findFirst().orElse(null);
        Job planMax = planJobs.stream().filter(j -> j.getId().equals(maxJob.getId())).findFirst().orElse(null);
        if (planMin == null || planMax == null) return null;
        int indexMin = jobs.indexOf(planMin);
        int indexMax = jobs.indexOf(planMax);
        if (indexMin < 0 || indexMax < 0) return null;
        return (indexMin < indexMax) ? new PlanTarget(planMin, indexMin) : new PlanTarget(planMax, indexMax);
    }

    private boolean tryUpdateExistingAlignMaintenance(PackagingSchedule schedule,
                                                     Line line,
                                                     List<Job> jobs,
                                                     Job targetPlanJob,
                                                     LocalDateTime factStart) {
        Job previousJob = targetPlanJob.getPreviousJob();
        boolean hasAlignMaintenance = previousJob != null
                && previousJob.isMaintenance()
                && previousJob.getMaintenanceTypeId() != null
                && previousJob.getMaintenanceTypeId() == 8;
        if (!hasAlignMaintenance) return false;

        LocalDateTime alignStart = previousJob.getStartProductionDateTime();

        long newDuration = Duration.between(alignStart, factStart).toMinutes();

        if (newDuration < 0) {
            newDuration = 0;
        }

        if (previousJob.getDuration().toMinutes() == newDuration) {
            return true;
        }
        MaintenanceRequest updateRequest = new MaintenanceRequest();
        updateRequest.setLineId(line.getId());
        updateRequest.setUpdateIndex(jobs.indexOf(previousJob));
        updateRequest.setDurationMinutes((int) newDuration);
        maintenanceJob.updateDuration(schedule, updateRequest);
        return true;
    }

    private record PlanTarget(Job job, int index) {}
    private record NpBounds(Job min, Job max) {}

}
