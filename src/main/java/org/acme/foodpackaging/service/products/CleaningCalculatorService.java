package org.acme.foodpackaging.service.products;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.Setter;

import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.record.CleaningResult;

import java.time.Duration;
import java.util.*;


@Getter
@Setter
@ApplicationScoped
public class CleaningCalculatorService {

    private List<CleaningRule> rules;
    /**
     * Определяет длительность мойки при переходе от продукта *from* к продукту *to*.
     * Логика:
     * 1. Проверяем каждый параметр (тип, глазурь, масса, наполнитель).
     * 2. Для каждого подбор подходит лучшая (наиболее специфичная) CleaningRule.
     * 3. Для всех четырёх параметров собираем найденные длительности.
     * 4. Итоговое время мойки — максимальное из четырёх (жёсткое правило цеха).
     */
    public CleaningResult getCleaningResult(Product from, Product to) {

        List<CleaningResult> results = new ArrayList<>();
    
        results.add(findRuleResult("1", from.getType(), to.getType()));
        results.add(findRuleResult("2", from.getGlaze(), to.getGlaze()));
        results.add(findRuleResult("3", from.getCurdMass(), to.getCurdMass()));
        results.add(findRuleResult("4", from.getFilling(), to.getFilling()));
    
        return results.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(CleaningResult::minutes))
                .orElse(CleaningResult.zero());
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
    private CleaningResult findRuleResult(String parameter, String from, String to) {
        return rules.stream()
                .filter(r -> r.parameter().equals(parameter))
                .filter(r -> matches(r.from(), from) && matches(r.to(), to))
                .sorted((r1, r2) -> Integer.compare(
                        specificity(r2, from, to),
                        specificity(r1, from, to)
                ))
                .map(r -> new CleaningResult(r.duration(), r.isPLRLC()))
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
        if (!rule.from().isBlank() && rule.from().equalsIgnoreCase(from)) score++;
        if (!rule.to().isBlank() && rule.to().equalsIgnoreCase(to)) score++;
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
    public void cleaningCalculate(List<Product> products) {

        for (Product current : products) {
    
            Map<Product, Duration> durations = new HashMap<>();
            Map<Product, CleaningResult> results = new HashMap<>();
    
            for (Product previous : products) {
    
                CleaningResult result;
    
                if (isMaintenance(current, previous)) {
                    result = CleaningResult.zero();
                }
                else if (current.getId().equals(previous.getId())) {
                    result = CleaningResult.zero();
                }
                else if (isSameProductDifferentPackaging(current, previous)) {
                    result = new CleaningResult(10, false);
                }
                else {
                    result = getCleaningResult(previous, current);
                }
    
                durations.put(previous, Duration.ofMinutes(result.minutes()));
                results.put(previous, result);
            }
    
            current.setCleaningDurations(durations);
            current.setCleaningResults(results);
        }
    }
    
    private boolean isMaintenance(Product current, Product previous) {
        return "MAINTENANCE".equals(current.getId())
            || "MAINTENANCE".equals(previous.getId());
    }
    
    private boolean isSameProductDifferentPackaging(Product a, Product b) {
        return a.getType().equals(b.getType())
            && a.getGlaze().equals(b.getGlaze())
            && a.getCurdMass().equals(b.getCurdMass())
            && a.getFilling().equals(b.getFilling())
            && !a.getId().equals(b.getId());
    }
    
}


