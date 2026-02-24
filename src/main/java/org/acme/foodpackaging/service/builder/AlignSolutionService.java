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
import java.util.stream.Collectors;

@ApplicationScoped
public class AlignSolutionService {

    private final MaintenanceJob maintenanceJob;

    public AlignSolutionService(MaintenanceJob maintenanceJob) {
        this.maintenanceJob = maintenanceJob;
    }

    public void alignByFactDuration(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            List<Job> jobs = line.getJobs();
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }
            List<MaintenanceToInsert> toInsert = collectMaintenanceToInsert(jobs);
            insertMaintenanceItems(schedule, line, toInsert);
        }
    }

    private List<MaintenanceToInsert> collectMaintenanceToInsert(List<Job> jobs) {
        List<MaintenanceToInsert> toInsert = new ArrayList<>();
        int acceptableDiff = 5;
        for (Job job : jobs) {
            Long factMinutes = calculateFactMinutes(job);
            if (factMinutes == null) {
                continue;
            }
            long planMinutes = calculatePlanMinutes(job);
            long diff = factMinutes - planMinutes;

            if (diff > acceptableDiff && !hasDeviationMaintenance(job)) {
                toInsert.add(new MaintenanceToInsert(job, diff));
            }
        }
        return toInsert;
    }

    private boolean hasDeviationMaintenance(Job job) {

        Job next = job.getNextJob();
        if (next == null) {
            return false;
        }

        return next.isMaintenance()
                && (next.getMaintenanceTypeId() == 7
                || next.getMaintenanceTypeId() == 8);
    }

    private boolean hasStartShiftMaintenance(Job job) {
        Job previous = job.getPreviousJob();
        if (previous == null) {
            return false;
        }

        return previous.isMaintenance()
                && previous.getMaintenanceTypeId() == 8;
    }

    private void insertMaintenanceItems(PackagingSchedule schedule, Line line,
                                        List<MaintenanceToInsert> toInsert) {
        for (int i = toInsert.size() - 1; i >= 0; i--) {
            MaintenanceToInsert item = toInsert.get(i);
            int index = line.getJobs().indexOf(item.job);
            if (index < 0) {
                continue;
            }
            MaintenanceRequest request = new MaintenanceRequest();
            request.setLineId(line.getId());
            request.setInsertIndex(index + 1);
            request.setDurationMinutes((int) item.diffMinutes);
            request.setMaintenanceTypeId(7);
            request.setMaintenanceNote(
                    "Отклонение факт > план. Job id=" + item.job.getId()
            );
            maintenanceJob.addMaintenanceJob(schedule, request);
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

    private record MaintenanceToInsert(Job job, long diffMinutes) {
    }

    public void alignLineStartByFact(PackagingSchedule schedule) {
        for (Line line : schedule.getLines()) {
            alignLineStartByFactForLine(schedule, line);
        }
    }

    private void alignLineStartByFactForLine(PackagingSchedule schedule, Line line) {

        List<Job> jobs = line.getJobs();
        if (jobs == null || jobs.isEmpty()) return;

        // ---------------------------------------------------------
        // 1. Берем все задачи с фактом и сортируем по старту
        // ---------------------------------------------------------
        List<Job> factJobs = jobs.stream()
                .filter(j -> !j.isMaintenance())
                .filter(j -> j.getProduct() != null)
                .filter(j -> j.getCameraStart() != null)
                .filter(j->j.getCameraEnd()!=null)
                .sorted(Comparator.comparing(Job::getCameraStart))
                .toList();

        if (factJobs.isEmpty()) return;

        Map<String, List<List<Job>>> chainsByProduct = new LinkedHashMap<>();

        int i = factJobs.size() - 1;

        while (i >= 0 && chainsByProduct.size() < 3) {

            Job last = factJobs.get(i);
            String productId = last.getProduct().getId();

            List<Job> chain = new ArrayList<>();
            chain.add(last);

            int j = i - 1;

            // собираем подряд идущую цепочку этого продукта
            while (j >= 0) {
                Job current = factJobs.get(j);

                if (!productId.equals(current.getProduct().getId())) {
                    break;
                }

                chain.add(current);
                j--;
            }

            // сортируем цепочку по времени (от раннего к позднему)
            chain.sort(Comparator.comparing(Job::getCameraStart));

            // добавляем в map
            chainsByProduct
                    .computeIfAbsent(productId, k -> new ArrayList<>())
                    .add(chain);

            // переходим к следующей позиции
            i = j;
        }

       List< List<Job>> factLists = chainsByProduct.get(factJobs.getLast().getProduct().getId());
        List<Job> factChain = factLists.getLast();
        String productId = factChain.getLast().getProduct().getId();

        List<Job> planJobs = jobs.stream()
                .filter(j -> !j.isMaintenance())
                .filter(j -> j.getProduct() != null)
                .filter(j -> productId.equals(j.getProduct().getId()))
                .toList();

        if (planJobs.size() < 2) return;
// ---------------------------------------------------------
// 5. Берем min / max np внутри цепочки
// ---------------------------------------------------------

        Job minNpJob = factChain.stream()
                .min(Comparator.comparing(Job::getNp))
                .orElse(null);

        Job maxNpJob = factChain.stream()
                .max(Comparator.comparing(Job::getNp))
                .orElse(null);

        if (minNpJob == null)
            return;

// ---------------------------------------------------------
// 6. Находим их в плане
// ---------------------------------------------------------

        Job planMin = planJobs.stream()
                .filter(j -> j.getId().equals(minNpJob.getId()))
                .findFirst()
                .orElse(null);

        Job planMax = planJobs.stream()
                .filter(j -> j.getId().equals(maxNpJob.getId()))
                .findFirst()
                .orElse(null);

        if (planMin == null || planMax == null)
            return;

        int indexMin = jobs.indexOf(planMin);
        int indexMax = jobs.indexOf(planMax);

        if (indexMin < 0 || indexMax < 0)
            return;

// ---------------------------------------------------------
// 7. Кто раньше в плане — тот опорный
// ---------------------------------------------------------

        Job targetPlanJob;
        int insertIndex;

        if (indexMin < indexMax) {
            targetPlanJob = planMin;
            insertIndex = indexMin;
        } else {
            targetPlanJob = planMax;
            insertIndex = indexMax;
        }

        // ---------------------------------------------------------
// 8. Первый элемент фактической цепочки по времени
// ---------------------------------------------------------
        Job firstFact = factChain.getFirst();
        LocalDateTime factStart = firstFact.getCameraStart();

        LocalDateTime planStart = targetPlanJob.getStartProductionDateTime();

        if (planStart == null) {
            return;
        }

// если план уже начинается позже или ровно в то же время — ничего не делаем
        if (!planStart.isBefore(factStart)) {
            return;
        }

        long diffMinutes =
                ceilMinutes(Duration.between(planStart, factStart));

        if (diffMinutes <= 0) {
            return;
        }

        // ---------------------------------------------------------
        // 9. Создаем ОДНУ сервисную операцию
        // ---------------------------------------------------------
        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId(line.getId());
        request.setInsertIndex(insertIndex);
        request.setDurationMinutes((int) diffMinutes);
        request.setMaintenanceTypeId(8);
        request.setMaintenanceNote(
                "Выравнивание последней фактической цепочки продукта "
                        + productId + ", batch="
        );

        maintenanceJob.addMaintenanceJob(schedule, request);
    }
    }

