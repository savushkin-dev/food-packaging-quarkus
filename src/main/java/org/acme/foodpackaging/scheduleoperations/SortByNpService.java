package org.acme.foodpackaging.scheduleoperations;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.SortRangeRequest;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

import java.util.*;
/**
 * Сервис выполняет перестановку задач на всех линиях,
 * сортируя их внутри каждого продуктового пула по полю NP.
 *
 * Логика чередования:
 * - если продукт присутствует на нескольких линиях,
 *   на первой линии задачи идут в отсортированном (прямом) порядке,
 *   на второй линии — в обратном,
 *   затем снова в прямом, и так далее (чередование по чётности появления).
 *
 * Разбиение по «подцепочкам»:
 * - внутри линии подцепочки определяются границами по событиям мойки
 *   (если у задачи startCleaning < startProduction),
 * - и границами задач типа "maintenance".
 *
 * Сервис не меняет структуру Schedule, но пересобирает списки задач внутри линий.
 */
@ApplicationScoped
public class SortByNpService {

    /**
     * Главный метод: перестраивает порядок задач во всём расписании.
     * Выполняет четыре ключевых шага:
     * 1) формирует продуктовые пулы,
     * 2) сортирует задачи внутри каждого пула по полю NP,
     * 3) для каждой линии пересобирает порядок задач,
     * 4) обновляет общий список задач в расписании.
     */
    public void reorderJobsByProductNp(PackagingSchedule schedule) {

        // Группирует задачи по продуктам в пулы
        Map<Product, Deque<Job>> pools = buildPools(schedule);

        // Сортирует каждый пул по NP
        sortPools(pools);

        // Счетчик появления каждого продукта (для чередования прямой/реверсивной сортировки)
        Map<Product, Integer> appearanceCounter = new HashMap<>();

        // Перестройка задач на всех линиях
        reorderLines(schedule, pools, appearanceCounter);

        // Пересборка общего списка задач в schedule
        rebuildScheduleJobList(schedule);
    }

    // ========================================================================
    //                          ПОСТРОЕНИЕ ПУЛОВ
    // ========================================================================

    /**
     * Формирует пулы задач по продуктам.
     * В каждый пул попадают только производственные задачи (не maintenance).
     */
    private Map<Product, Deque<Job>> buildPools(PackagingSchedule schedule) {
        Map<Product, Deque<Job>> pools = new HashMap<>();

        for (Job job : schedule.getJobs()) {
            if (!job.isMaintenance() && job.getLineId() == null) {
                pools.computeIfAbsent(job.getProduct(), p -> new ArrayDeque<>()).add(job);
            }
        }
        return pools;
    }

    // ========================================================================
    //                          СОРТИРОВКА ПУЛОВ
    // ========================================================================

    /**
     * Сортирует каждую очередь задач продукта по полю NP.
     * После сортировки очередь полностью пересоздаётся.
     */
    private void sortPools(Map<Product, Deque<Job>> pools) {
        for (Deque<Job> deque : pools.values()) {
            List<Job> sorted = new ArrayList<>(deque);
            sorted.sort(Comparator.comparing(Job::getNp));

            deque.clear();
            deque.addAll(sorted);
        }
    }

    // ========================================================================
    //                    ПЕРЕСТРОЙКА ЗАДАЧ НА ВСЕХ ЛИНИЯХ
    // ========================================================================

    /**
     * На каждой линии:
     * - определяет, сколько задач каждого продукта должно остаться,
     * - подготавливает для них соответствующие итераторы из пулов,
     * - разбивает линию на подцепочки по границам мойки и maintenance,
     * - заменяет задачи внутри подцепочек задачами из итераторов.
     *
     * Итераторы учитывают чётность появления продукта на линиях
     * и могут инвертировать порядок раздачи.
     */
    private void reorderLines(
            PackagingSchedule schedule,
            Map<Product, Deque<Job>> pools,
            Map<Product, Integer> appearanceCounter
    ) {
        for (Line line : schedule.getLines()) {

            List<Job> original = line.getJobs();
            List<Job> newOrder = new ArrayList<>();

            // Считает, сколько задач каждого продукта должно быть на линии
            Map<Product, Integer> requiredCount = extractRequiredCounts(original);

            // Готовит итераторы для каждого продукта
            Map<Product, Iterator<Job>> productIterators =
                    prepareIterators(pools, requiredCount, appearanceCounter);

            // Собирает линию из подцепочек
            processLineChunks(original, productIterators, newOrder);

            // Восстанавливает связи previous/next
            fixPrevNext(newOrder);

            // Увеличивает счётчики появления продуктов
            for (Product p : requiredCount.keySet()) {
                appearanceCounter.put(p, appearanceCounter.getOrDefault(p, 0) + 1);
            }

            line.setJobs(newOrder);
        }
    }

    // ========================================================================
    //                         ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ========================================================================

    /**
     * Подсчитывает, сколько задач каждого продукта встречается в исходном списке.
     * Используется для выделения ровно такого же количества задач из пула.
     */
    private Map<Product, Integer> extractRequiredCounts(List<Job> original) {
        Map<Product, Integer> required = new LinkedHashMap<>();
        for (Job j : original) {
            if (!j.isMaintenance() && j.getLineId() == null) {
                Product p = j.getProduct();
                required.put(p, required.getOrDefault(p, 0) + 1);
            }
        }
        return required;
    }

    /**
     * Извлекает из пулов необходимое количество задач для каждого продукта.
     * Если продукт появляется на линии в нечётный раз — инвертирует порядок
     * (реверс), обеспечивая чередование прямой/обратной сортировки по линиям.
     */
    private Map<Product, Iterator<Job>> prepareIterators(
            Map<Product, Deque<Job>> pools,
            Map<Product, Integer> required,
            Map<Product, Integer> appearanceCounter
    ) {
        Map<Product, Iterator<Job>> iters = new HashMap<>();

        for (Map.Entry<Product, Integer> e : required.entrySet()) {
            Product product = e.getKey();
            int need = e.getValue();

            Deque<Job> pool = pools.get(product);
            if (pool == null || pool.size() < need) {
                throw new IllegalStateException(
                        "Not enough jobs in pool for product " + product.getName());
            }
            // Забирает нужное количество задач в линию
            List<Job> portion = new ArrayList<>(need);
            for (int i = 0; i < need; i++) portion.add(pool.pollFirst());
            // Чередует порядок на каждой следующей линии
            int appearance = appearanceCounter.getOrDefault(product, 0);
            if ((appearance % 2) == 1) Collections.reverse(portion);

            iters.put(product, portion.iterator());
        }

        return iters;
    }
    /**
     * Перебирает задачи линии и формирует новую последовательность.
     * Использует буфер «подцепочки», который сбрасывается при:
     * - обнаружении границы мойки,
     * - встрече задач maintenance.
     */
    private void processLineChunks(
            List<Job> original,
            Map<Product, Iterator<Job>> iters,
            List<Job> result
    ) {
        List<Job> buffer = new ArrayList<>();

        for (Job job : original) {
            if (job.getLineId() != null || job.isMaintenance()) {
                flushBuffer(buffer, result, iters);
                result.add(job);
                continue;
            }
            if (hadCleaningBefore(job)) {
                flushBuffer(buffer, result, iters);
            }
            buffer.add(job);
        }
        flushBuffer(buffer, result, iters);
    }

    private void flushBuffer(List<Job> buffer, List<Job> result, Map<Product, Iterator<Job>> iters) {
        if (!buffer.isEmpty()) {
            result.addAll(fillSubchain(buffer, iters));
            buffer.clear();
        }
    }
    /**
     * Проверяет, была ли мойка перед задачей.
     * Мойка считается, если startCleaning < startProduction.
     */
    private boolean hadCleaningBefore(Job job) {
        if (job.getStartCleaningDateTime() == null || job.getStartProductionDateTime() == null)
            return false;
        return job.getStartCleaningDateTime().isBefore(job.getStartProductionDateTime());
    }
    /**
     * Заполняет подцепочку задачами из соответствующих итераторов продуктов.
     * Итераторы гарантируют правильный порядок и количество задач.
     */
    private List<Job> fillSubchain(List<Job> subchain, Map<Product, Iterator<Job>> iters) {
        List<Job> res = new ArrayList<>(subchain.size());
        for (Job old : subchain) {
            Product p = old.getProduct();
            Iterator<Job> it = iters.get(p);
            if (it == null || !it.hasNext()) {
                throw new IllegalStateException(
                        "Iterator is missing or exhausted for product " + p.getName());
            }
            res.add(it.next());
        }
        return res;
    }
    /**
     * Перестраивает связи previous/next внутри результирующего списка задач линии.
     */
    private void fixPrevNext(List<Job> jobs) {
        for (int i = 0; i < jobs.size(); i++) {
            Job prev = (i > 0) ? jobs.get(i - 1) : null;
            Job next = (i < jobs.size() - 1) ? jobs.get(i + 1) : null;
            Job cur = jobs.get(i);

            cur.setPreviousJob(prev);
            cur.setNextJob(next);
        }
    }
    /**
     * Пересобирает общий список задач расписания, объединяя задачи всех линий.
     * Также вызывает fixLineJobs(line), чтобы восстановить корректность расписания.
     */
    private void rebuildScheduleJobList(PackagingSchedule schedule) {
        List<Job> combined = new ArrayList<>();
        for (Line line : schedule.getLines()) {
            fixLineJobs(line);
            combined.addAll(line.getJobs());
        }
        schedule.setJobs(combined);
    }
    /**
     * Сортирует дипозон задач на линии по партиям в прямом/обратном в зависимости от флага sortUp
     */
    public void sortRangeByNp(PackagingSchedule schedule, SortRangeRequest request) {
        Line line = findLineById(schedule, request.getLineId());
        List<Job> jobs = Optional.ofNullable(line.getJobs()).orElse(Collections.emptyList());

        int from = request.getFromIndex();
        int sortCount = request.getSortCount();

        if (from < 0) throw new IllegalArgumentException("fromIndex must be non-negative");
        if (sortCount <= 0) throw new IllegalArgumentException("sortCount must be positive");

        int to = Math.min(from + sortCount, jobs.size());

        List<Job> sublist = new ArrayList<>(jobs.subList(from, to));
        removePackagingExtension(sublist);

        if (request.isSortUp()) {
            sublist.sort(Comparator.comparing(Job::getNp));
        } else {
        sublist.sort(Comparator.comparing(Job::getNp).reversed());
    }

        // заменяем подсписок в исходной линии на отсортированный и очищенный
        for (int i = 0; i < sublist.size(); i++) {
            jobs.set(from + i, sublist.get(i));
        }

        while (jobs.size() > from + sublist.size()) {
            jobs.remove(from + sublist.size());
        }

    line.setJobs(jobs);
}
}
