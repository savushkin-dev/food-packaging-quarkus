package org.acme.foodpackaging.service.materials.config;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.materials.PlrSprog;
import org.acme.foodpackaging.repository.materials.SprogRepository;

import java.time.LocalDate;
import java.time.YearMonth;

@ApplicationScoped
public class SprogService {

    private final SprogRepository sprogRepository;

    @Inject
    public SprogService(SprogRepository sprogRepository) {
        this.sprogRepository = sprogRepository;
    }

    /** Найти программу на месяц. Если нет — кидаем исключение. */
    @CacheResult(cacheName = "sprog-cache")
    public PlrSprog findByDate(LocalDate date) {
        return sprogRepository.findByDateOptional(date)
                .orElseThrow(() -> new RuntimeException(
                        "Не найдена программа для месяца: " + YearMonth.from(date)
                ));
    }

    @CacheInvalidateAll(cacheName = "sprog-cache")
    public void invalidateAll() {
        // Очищает весь кэш программ
    }

    @CacheInvalidateAll(cacheName = "rnpp-cache")
    public void deleteAll(){
        invalidateAll();
        sprogRepository.deleteAll();
    }

    /** Текущий месяц — обязателен. Бросает исключение, если программы нет. */
    public double sysnNow() {
        return findByDate(LocalDate.now()).sysn;
    }

    /** Следующий месяц — опционально. null, если программы нет. */
    public Double sysnNextOrNull() {
        return sprogRepository.findByDateOptional(LocalDate.now().plusMonths(1))
                .map(p -> p.sysn)
                .orElse(null);
    }
}