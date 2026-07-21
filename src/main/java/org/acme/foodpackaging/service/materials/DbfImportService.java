package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.acme.foodpackaging.entity.materials.Rnpp;
import org.acme.foodpackaging.entity.materials.Sprog;
import org.acme.foodpackaging.repository.materials.RnppRepository;
import org.acme.foodpackaging.repository.materials.SprogRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ApplicationScoped
public class DbfImportService {

    private static final int BATCH_SIZE = 20;
    private static final DateTimeFormatter DBF_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Inject
    DbfReaderService dbfReaderService;

    @Inject
    SprogRepository sprogRepository;

    @Inject
    RnppRepository rnppRepository;

    @Inject
    EntityManager entityManager;

    /**
     * Импорт SPROG с ПАКЕТНОЙ вставкой
     */
    public int importSprog(String dbfPath) {
        log.info("=== START SPROG IMPORT ===");
        long startTime = System.currentTimeMillis();

        // Используем AtomicInteger для счетчика в лямбде
        AtomicInteger importedCount = new AtomicInteger(0);
        List<Sprog> batch = new ArrayList<>(BATCH_SIZE);

        try {
            // Потоковое чтение
            dbfReaderService.readDbfFileStreaming(dbfPath, (Map<String, Object> record) -> {
                try {
                    Sprog entity = mapToSprog(record);
                    batch.add(entity);

                    // Когда набрали BATCH_SIZE - сохраняем пакетом
                    if (batch.size() >= BATCH_SIZE) {
                        int saved = saveSprogBatch(batch);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to map SPROG record", e);
                }
            });

            // Сохраняем остаток
            if (!batch.isEmpty()) {
                int saved = saveSprogBatch(batch);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== FINISHED SPROG IMPORT: {} records in {} sec ===", importedCount.get(), totalTime);
            return importedCount.get();

        } catch (Exception e) {
            log.error("SPROG import failed", e);
            throw new RuntimeException("Failed to import SPROG", e);
        }
    }

    /**
     * Импорт RNPP с ПАКЕТНОЙ вставкой
     */
    public int importRnpp(String dbfPath) {
        log.info("=== START RNPP IMPORT ===");
        long startTime = System.currentTimeMillis();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<Rnpp> batch = new ArrayList<>(BATCH_SIZE);

        try {
            // Ищем memo файл
            String basePath = dbfPath.substring(0, dbfPath.lastIndexOf('.'));
            String dbtPath = basePath + ".DBT";
            String fptPath = basePath + ".FPT";

            String memoPath = null;
            if (new java.io.File(dbtPath).exists()) {
                memoPath = dbtPath;
            } else if (new java.io.File(fptPath).exists()) {
                memoPath = fptPath;
            }

            // Потоковое чтение с memo файлом
            dbfReaderService.readDbfFileStreaming(dbfPath, memoPath, "CP866", (Map<String, Object> record) -> {
                try {
                    Rnpp entity = mapToRnpp(record);
                    batch.add(entity);

                    // Когда набрали BATCH_SIZE - сохраняем пакетом
                    if (batch.size() >= BATCH_SIZE) {
                        int saved = saveRnppBatch(batch);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to map RNPP record", e);
                }
            });

            // Сохраняем остаток
            if (!batch.isEmpty()) {
                int saved = saveRnppBatch(batch);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== FINISHED RNPP IMPORT: {} records in {} sec ===", importedCount.get(), totalTime);
            return importedCount.get();

        } catch (Exception e) {
            log.error("RNPP import failed", e);
            throw new RuntimeException("Failed to import RNPP", e);
        }
    }

    /**
     * Пакетное сохранение SPROG
     */
    @Transactional
    public int saveSprogBatch(List<Sprog> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        // 🔥 ПАКЕТНАЯ ВСТАВКА - один вызов на весь список
        sprogRepository.persist(batch);

        // Очищаем кеш
        entityManager.flush();
        entityManager.clear();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Saved SPROG batch of {} records in {} ms", batch.size(), duration);

        return batch.size();
    }

    /**
     * Пакетное сохранение RNPP
     */
    @Transactional
    public int saveRnppBatch(List<Rnpp> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        // 🔥 ПАКЕТНАЯ ВСТАВКА - один вызов на весь список
        rnppRepository.persist(batch);

        // Очищаем кеш
        entityManager.flush();
        entityManager.clear();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Saved RNPP batch of {} records in {} ms", batch.size(), duration);

        return batch.size();
    }

    // ============ МАППИНГИ ============

    private Sprog mapToSprog(Map<String, Object> record) {
        Sprog entity = new Sprog();

        entity.setSysn(getDoubleOrDefault(record, "SYSN", 0.0));
        entity.setDt1(getDateOrDefault(record, "DT1", LocalDate.now()));
        entity.setDt2(getDateOrDefault(record, "DT2", LocalDate.now().plusDays(1)));
        entity.setObj(getStringOrDefault(record, "OBJ", "UNKNOWN"));
        entity.setNp(getIntegerOrDefault(record, "NP", 0));

        return entity;
    }

    private Rnpp mapToRnpp(Map<String, Object> record) {
        Rnpp entity = new Rnpp();

        entity.setSysn(getDoubleOrDefault(record, "SYSN", 0.0));
        entity.setKmc(getStringOrDefault(record, "KMC", "UNKNOWN"));
        entity.setEmk(getDoubleOrDefault(record, "EMK", 0.0));

        String kkom = getStringOrDefault(record, "KKOM", "UNKNOWN");
        if (kkom.length() > 10) {
            kkom = kkom.substring(0, 10);
        }
        entity.setKkom(kkom);

        entity.setKol1t(getDoubleOrDefault(record, "KOL1T", 0.0));
        entity.setKolvk(getDoubleOrDefault(record, "KOLVK", 0.0));

        return entity;
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ============

    private String getString(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null) return null;
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }

    private String getStringOrDefault(Map<String, Object> record, String key, String defaultValue) {
        String value = getString(record, key);
        return value != null ? value : defaultValue;
    }

    private Double getDouble(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double getDoubleOrDefault(Map<String, Object> record, String key, Double defaultValue) {
        Double value = getDouble(record, key);
        return value != null ? value : defaultValue;
    }

    private Integer getInteger(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getIntegerOrDefault(Map<String, Object> record, String key, Integer defaultValue) {
        Integer value = getInteger(record, key);
        return value != null ? value : defaultValue;
    }

    private LocalDate getDate(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null) return null;

        try {
            if (value instanceof java.util.Date) {
                return ((java.util.Date) value).toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            }
            String str = value.toString().trim();
            return LocalDate.parse(str, DBF_DATE_FORMAT);
        } catch (Exception e) {
            log.warn("Failed to parse date: {} for field {}", value, key);
            return null;
        }
    }

    private LocalDate getDateOrDefault(Map<String, Object> record, String key, LocalDate defaultValue) {
        LocalDate value = getDate(record, key);
        return value != null ? value : defaultValue;
    }
}