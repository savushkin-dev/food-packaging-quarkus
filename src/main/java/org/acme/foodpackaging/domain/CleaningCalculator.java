package org.acme.foodpackaging.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CleaningCalculator {
    private final List<CleaningRule> rules;

    public CleaningCalculator(List<CleaningRule> rules) {
        this.rules = rules;
    }

    public int getCleaningTime(Product from, Product to) {
        List<Integer> times = new ArrayList<>();

        times.add(findDuration("1", from.getType(), to.getType()));       // тип продукта
        times.add(findDuration("2", from.getGlaze(), to.getGlaze()));     // глазурь
        times.add(findDuration("3", from.getCurdMass(), to.getCurdMass())); // творожная масса
        times.add(findDuration("4", from.getFilling(), to.getFilling()));   // наполнитель

        return times.stream()
                .filter(Objects::nonNull)
                .max(Integer::compare) // берём самое большое время
                .orElse(0);
    }

    private boolean matches(String ruleValue, String actual) {
        if (ruleValue == null || ruleValue.isBlank()) return true; // ANY
        return ruleValue.equalsIgnoreCase(actual);
    }

    private Integer findDuration(String parameter, String from, String to) {
        String fromVal = from == null ? "" : from;
        String toVal   = to == null ? "" : to;

        return rules.stream()
                .filter(r -> r.getParameter().equals(parameter))
                .filter(r -> matches(r.getFrom(), fromVal) && matches(r.getTo(), toVal))
                .sorted((r1, r2) -> Integer.compare(
                        specificity(r2, fromVal, toVal),
                        specificity(r1, fromVal, toVal)
                ))
                .map(CleaningRule::getDuration)
                .findFirst()
                .orElse(null);
    }

    private int specificity(CleaningRule rule, String from, String to) {
        int score = 0;
        if (!rule.getFrom().isBlank() && rule.getFrom().equalsIgnoreCase(from)) score++;
        if (!rule.getTo().isBlank() && rule.getTo().equalsIgnoreCase(to)) score++;
        return score;
    }
}
