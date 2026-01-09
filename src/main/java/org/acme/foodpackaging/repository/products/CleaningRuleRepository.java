package org.acme.foodpackaging.repository.products;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import org.acme.foodpackaging.entity.products.PlrChange;
import org.acme.foodpackaging.record.CleaningRule;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class CleaningRuleRepository implements PanacheRepository<PlrChange> {

    @ConfigProperty(name = "krc")
    String lineId;

    public List<CleaningRule> loadRules() {

        List<PlrChange> rows =
                find("deletedFlag = 0 and lineId = ?1 and duration is not null order by parameter",
                        lineId)
                        .list();

        return rows.stream()
                .map(r -> new CleaningRule(
                        r.parameter,
                        r.from, r.to,
                        r.duration
                ))
                .toList();
    }
}
