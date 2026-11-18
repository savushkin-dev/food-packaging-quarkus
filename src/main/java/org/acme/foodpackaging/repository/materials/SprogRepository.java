package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.PlrSprog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@ApplicationScoped
public class SprogRepository implements PanacheRepository<PlrSprog> {

    private static final Integer NP = 1;

    @ConfigProperty(name = "app.sprog.obj")
    String obj;

    /** Найти программу на месяц. Если нет — вернуть null (или Optional.empty). */
    public Optional<PlrSprog> findByDateOptional(LocalDate date) {
        YearMonth yearMonth = YearMonth.from(date);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay  = yearMonth.atEndOfMonth();

        return find(
                "obj = ?1 AND np = ?2 AND dt1 >= ?3 AND dt2 <= ?4",
                obj, NP, firstDay, lastDay
        ).firstResultOptional();
    }
}