package org.acme.foodpackaging.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CleaningCalculator {
    private final List<CleaningRule> rules;

    public CleaningCalculator(List<CleaningRule> rules) {
        this.rules = rules;
    }

    public int getCleaningTime(Product from, Product to) {
        List<Integer> times = new ArrayList<>();

        times.add(findDuration("3", from.getCurdMass(), to.getCurdMass())); // творожная масса
        times.add(findDuration("2", from.getGlaze(), to.getGlaze()));    //  глазурь
        times.add(findDuration("4", from.getFilling(), to.getFilling()));  // наполнитель

        return times.stream()
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(0); // если правил нет
    }

    private Integer findDuration(String parameter, String from, String to) {
        return rules.stream()
                .filter(r -> r.getParameter().equals(parameter))
                .filter(r ->
                        (r.getFrom().equalsIgnoreCase(from) || r.getFrom().isBlank()) &&
                                (r.getTo().equalsIgnoreCase(to) || r.getTo().isBlank())
                )
                .map(CleaningRule::getDuration)
                .findFirst()
                .orElse(null);
    }
}
