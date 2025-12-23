package org.acme.foodpackaging.service.products;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.CleaningRule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.sql.SqlQueries;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.*;



@ApplicationScoped
public class CleaningCalculatorService {
    private List<CleaningRule> rules;

    @Inject
    @ConfigProperty(name = "db.url")
    String dbUrl;

    @Inject
    SqlQueries sqlQueries;
    /**
     * Определяет длительность мойки при переходе от продукта *from* к продукту *to*.
     * Логика:
     * 1. Проверяем каждый параметр (тип, глазурь, масса, наполнитель).
     * 2. Для каждого подбор подходит лучшая (наиболее специфичная) CleaningRule.
     * 3. Для всех четырёх параметров собираем найденные длительности.
     * 4. Итоговое время мойки — максимальное из четырёх (жёсткое правило цеха).
     */
    public int getCleaningTime(Product from, Product to) {
        List<Integer> times = new ArrayList<>();

        // Поиск длительности по каждому параметру (1 — тип продукта)
        times.add(findDuration("1", from.getType(), to.getType()));

        // 2 — глазурь
        times.add(findDuration("2", from.getGlaze(), to.getGlaze()));

        // 3 — масса сырка
        times.add(findDuration("3", from.getCurdMass(), to.getCurdMass()));

        // 4 — наполнитель
        times.add(findDuration("4", from.getFilling(), to.getFilling()));

        // Максимальная длительность определяет итоговое время мойки
        return times.stream()
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(0);
    }

    /**
     * Проверяет, подходит ли значение правила под фактический параметр.
     * Пустое ruleValue = wildcard (подходит ко всем значениям).
     */
    private boolean matches(String ruleValue, String actual) {
        if (ruleValue.isBlank()) return true;
        return ruleValue.equalsIgnoreCase(actual == null ? "" : actual);
    }
    /**
     * Поиск длительности по конкретному параметру.
     * Механика:
     * 1. Фильтруем правила данного параметра.
     * 2. Отбираем те, у которых совпадает FROM и TO (или пустые wildcard).
     * 3. Сортируем по "специфичности": чем больше конкретных совпадений, тем выше приоритет.
     * 4. Берём длительность самого специфичного правила.
     */
    private Integer findDuration(String parameter, String from, String to) {
        return rules.stream()
                .filter(r -> r.getParameter().equals(parameter))
                .filter(r -> matches(r.getFrom(), from) && matches(r.getTo(), to))
                .sorted((r1, r2) -> Integer.compare(
                        specificity(r2, from, to),
                        specificity(r1, from, to)
                ))
                .map(CleaningRule::getDuration)
                .findFirst()
                .orElse(null);
    }
    /**
     * Определяет "специфичность" правила:
     * +1 если точно совпало значение FROM
     * +1 если точно совпало значение TO
     * Таким образом:
     * — правило с двумя совпадениями (FROM, TO) приоритетнее всех
     * — правило с одним совпадением лучше чем wildcard
     * — полностью пустое правило (FROM="", TO="") — самое слабое
     */
    private int specificity(CleaningRule rule, String from, String to) {
        int score = 0;
        if (!rule.getFrom().isBlank() && rule.getFrom().equalsIgnoreCase(from)) score++;
        if (!rule.getTo().isBlank() && rule.getTo().equalsIgnoreCase(to)) score++;
        return score;
    }
    /**
     * Вычисляет длительности мойки для всех пар (previous → current)
     * и записывает в product.cleaningDurations.
     * <p>
     * Логика:
     * 1. Перебираем каждый продукт как "текущий".
     * 2. Для него считаем длительность перехода с каждого другого продукта.
     * 3. Учитываем спец-правила:
     * - переход с/на MAINTENANCE = без мойки
     * - если все параметры совпадают, но id разные → смена упаковки (10 минут)
     * - иначе — рассчитываем по правилам БД через getCleaningTime()
     */
    public void cleaningCalculate(List<Product> products, List<CleaningRule> rules) {

        this.rules = rules;

        for (Product current : products) {
            Map<Product, Duration> durations = new HashMap<>(products.size());

            for (Product previous : products) {
                Duration duration;

                // Мойка не нужна при переходе от/к тех. обслуживанию
                if (current.getId().equals("MAINTENANCE") || previous.getId().equals("MAINTENANCE")) {
                    duration = Duration.ZERO;
                }
                // Переход на тот же продукт — 0
                else if (current.getId().equals(previous.getId())) {
                    duration = Duration.ZERO;
                }
                // Все параметры идентичны, кроме ID ⇒ это тот же продукт, но другая упаковка
                else if (current.getType().equals(previous.getType())
                        && current.getGlaze().equals(previous.getGlaze())
                        && current.getCurdMass().equals(previous.getCurdMass())
                        && current.getFilling().equals(previous.getFilling())
                        && !current.getId().equals(previous.getId())) {

                    // На линии требуется только смена упаковочного материала
                    duration = Duration.ofMinutes(10);
                }
                // Общий случай: считаем по правилам БД
                else {
                    duration = Duration.ofMinutes(getCleaningTime(previous, current));
                }

                durations.put(previous, duration);
            }

            // Присваиваем рассчитанную таблицу переходов
            current.setCleaningDurations(durations);
        }
    }
}


