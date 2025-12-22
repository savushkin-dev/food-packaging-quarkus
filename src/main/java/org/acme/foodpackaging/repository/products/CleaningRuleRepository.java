package org.acme.foodpackaging.repository.products;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.CleaningRule;
import org.acme.foodpackaging.entity.products.CleaningRuleEntity;
import org.acme.foodpackaging.sql.SqlQueries;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class CleaningRuleRepository implements PanacheRepository<CleaningRuleEntity> {

    @ConfigProperty(name = "krc")
    String krc;

    public List<CleaningRule> loadRules() {

        List<CleaningRuleEntity> rows =
                find("deletedFlag = 0 and krc = ?1 and duration is not null order by npar",
                        krc)
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
