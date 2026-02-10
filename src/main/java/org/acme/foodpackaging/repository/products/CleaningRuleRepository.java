package org.acme.foodpackaging.repository.products;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import org.acme.foodpackaging.entity.products.PlrChange;
import org.acme.foodpackaging.record.CleaningRule;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class CleaningRuleRepository implements PanacheRepository<PlrChange> {

    @ConfigProperty(name = "krc")
    String lineId;

    public List<CleaningRule> loadRules() {

        List<PlrChange> rows =
                find(
                        "deletedFlag = 0 and lineId = ?1 and duration is not null order by parameter",
                        lineId)
                        .list();

        return rows.stream()
                .map(r -> new CleaningRule(
                        Objects.requireNonNullElse(r.parameter, ""),
                        Objects.requireNonNullElse(r.from, ""),
                        Objects.requireNonNullElse(r.to, ""),
                        r.duration,
                        Boolean.TRUE.equals(r.isPLRLC)
                ))
                .toList();
    }
}
