package org.acme.foodpackaging.repository.products;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.CleaningRule;
import org.acme.foodpackaging.entity.products.CleaningRuleEntity;

import java.util.List;

@ApplicationScoped
public class CleaningRuleRepository implements PanacheRepository<CleaningRuleEntity> {

    public List<CleaningRule> loadRules() {

        List<CleaningRuleEntity> rows =
                find("deletedFlag = 0 and krc = ?1 and duration is not null order by npar",
                        "170610000000")
                        .list();

        return rows.stream()
                .map(r -> new CleaningRule(
                        r.npar,
                        r.fromValue, r.toValue,
                        r.duration
                ))
                .toList();
    }
}
