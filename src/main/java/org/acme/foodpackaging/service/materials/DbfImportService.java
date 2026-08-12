package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.entity.materials.PlrPp;
import org.acme.foodpackaging.entity.materials.PlrRnpp;
import org.acme.foodpackaging.entity.materials.PlrSprog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private static final String DEFAULT_UNKNOWN = "UNKNOWN";
    private static final String DEFAULT_EMPTY = "";
    private static final int MIN_SYSN = 39000;

    private final DbfReaderService dbfReaderService;
    private final EntityManager entityManager;
    private final MtService mtService;
    private final PpService ppService;
    private final SprogService sprogService;
    private final RnppService rnppService;

    @Inject
    public DbfImportService(DbfReaderService dbfReaderService, EntityManager entityManager,
                            MtService mtService, PpService ppService,
                            SprogService sprogService, RnppService rnppService) {
        this.dbfReaderService = dbfReaderService;
        this.entityManager = entityManager;
        this.mtService = mtService;
        this.ppService = ppService;
        this.sprogService = sprogService;
        this.rnppService = rnppService;
    }

    // ==================== PUBLIC IMPORT METHODS ====================

    @Transactional
    public void importSprog(String dbfPath) {
        log.info("=== START SPROG IMPORT ===");
        long startTime = System.currentTimeMillis();

        // Очищаем таблицу перед импортом
        sprogService.deleteAll();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<PlrSprog> batch = new ArrayList<>(BATCH_SIZE);

        try {
            dbfReaderService.readDbfFileStreaming(dbfPath, (Map<String, Object> recordMap) -> {
                try {
                    PlrSprog entity = mapToSprog(recordMap);
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        int saved = saveSprogBatch(batch);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.warn("Failed to map SPROG record", e);
                }
            });

            if (!batch.isEmpty()) {
                int saved = saveSprogBatch(batch);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== FINISHED SPROG IMPORT: {} records in {} sec ===", importedCount.get(), totalTime);

        } catch (Exception e) {
            log.error("SPROG import failed", e);
            throw new RuntimeException("Failed to import SPROG", e);
        }
    }

    @Transactional
    public void importRnpp(String dbfPath) {
        log.info("=== START Rnpp IMPORT ===");
        long startTime = System.currentTimeMillis();

        // Очищаем таблицу перед импортом
        rnppService.deleteAll();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<PlrRnpp> batch = new ArrayList<>(BATCH_SIZE);

        try {
            String memoPath = findMemoFile(dbfPath);

            dbfReaderService.readDbfFileStreaming(dbfPath, memoPath, "CP866", (Map<String, Object> recordMap) -> {
                try {
                    if (((Number) recordMap.get("SYSN")).intValue() < MIN_SYSN) {
                        return;
                    }

                    PlrRnpp entity = mapToRnpp(recordMap);
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        int saved = saveRnppBatch(batch);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to map Rnpp record", e);
                }
            });

            if (!batch.isEmpty()) {
                int saved = saveRnppBatch(batch);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== FINISHED Rnpp IMPORT: {} records in {} sec ===", importedCount.get(), totalTime);

        } catch (Exception e) {
            log.error("Rnpp import failed", e);
            throw new RuntimeException("Failed to import Rnpp", e);
        }
    }

    @Transactional
    public void importMt(String dbfPath) {
        log.info("=== START Mt IMPORT ===");
        long startTime = System.currentTimeMillis();

        Map<String, PlrMt> existingMap = mtService.findAllAsMapByKmt();
        List<PlrMt> allEntitiesToSave = new ArrayList<>();

        try {
            dbfReaderService.readDbfFileStreaming(dbfPath, (Map<String, Object> recordMap) -> {
                try {
                    PlrMt newEntity = mapToMt(recordMap);
                    String kmt = newEntity.getKmt();

                    PlrMt existing = existingMap.get(kmt);

                    if (existing != null) {
                        // Обновляем только системные поля
                        existing.setKgr(newEntity.getKgr());
                        existing.setSnm(newEntity.getSnm());
                        existing.setEdu(newEntity.getEdu());
                        // pers и rnd не трогаем
                        allEntitiesToSave.add(existing);
                    } else {
                        allEntitiesToSave.add(newEntity);
                    }

                } catch (Exception e) {
                    log.warn("Failed to map Mt record", e);
                }
            });

            if (!allEntitiesToSave.isEmpty()) {
                saveMtBatch(allEntitiesToSave);
            }

            mtService.invalidateAll();

            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== FINISHED Mt IMPORT: {} records in {} sec ===", allEntitiesToSave.size(), totalTime);

        } catch (Exception e) {
            log.error("Mt import failed", e);
            throw new RuntimeException("Failed to import Mt", e);
        }
    }

    @Transactional
    public void importPp(String dbfPath) {
        log.info("=== START Pp IMPORT ===");
        long startTime = System.currentTimeMillis();

        // Очищаем таблицу перед импортом
        ppService.deleteAll();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<PlrPp> batch = new ArrayList<>(BATCH_SIZE);

        try {
            dbfReaderService.readDbfFileStreaming(dbfPath, (Map<String, Object> recordMap) -> {
                try {
                    PlrPp entity = mapToPp(recordMap);
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        int saved = savePpBatch(batch);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to map Pp record", e);
                }
            });

            if (!batch.isEmpty()) {
                int saved = savePpBatch(batch);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== FINISHED Pp IMPORT: {} records in {} sec ===", importedCount.get(), totalTime);

        } catch (Exception e) {
            log.error("Pp import failed", e);
            throw new RuntimeException("Failed to import Pp", e);
        }
    }

    // ==================== BATCH SAVE METHODS ====================

    private int saveSprogBatch(List<PlrSprog> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        for (PlrSprog entity : batch) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();

        log.debug("Saved SPROG batch of {} records in {} ms", batch.size(), System.currentTimeMillis() - startTime);
        return batch.size();
    }

    private int saveRnppBatch(List<PlrRnpp> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        for (PlrRnpp entity : batch) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();

        log.debug("Saved Rnpp batch of {} records in {} ms", batch.size(), System.currentTimeMillis() - startTime);
        return batch.size();
    }

    private void saveMtBatch(List<PlrMt> batch) {
        if (batch.isEmpty()) {
            return;
        }

        for (PlrMt entity : batch) {
            entityManager.merge(entity);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private int savePpBatch(List<PlrPp> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        for (PlrPp entity : batch) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();

        log.debug("Saved Pp batch of {} records in {} ms", batch.size(), System.currentTimeMillis() - startTime);
        return batch.size();
    }

    // ==================== MAPPER METHODS ====================

    private PlrSprog mapToSprog(Map<String, Object> recordMap) {
        PlrSprog entity = new PlrSprog();

        entity.setSysn(getDoubleOrDefault(recordMap, "SYSN", 0.0));
        entity.setDt1(getDateOrDefault(recordMap, "DT1", LocalDate.now(ZoneId.systemDefault())));
        entity.setDt2(getDateOrDefault(recordMap, "DT2", LocalDate.now(ZoneId.systemDefault()).plusDays(1)));
        entity.setObj(getStringOrDefault(recordMap, "OBJ", DEFAULT_UNKNOWN));
        entity.setNp(getIntegerOrDefault(recordMap, "NP", 0));

        return entity;
    }

    private PlrRnpp mapToRnpp(Map<String, Object> recordMap) {
        PlrRnpp entity = new PlrRnpp();

        entity.setSysn(getDoubleOrDefault(recordMap, "SYSN", 0.0));
        entity.setKmc(getStringOrDefault(recordMap, "KMC", DEFAULT_UNKNOWN));
        entity.setKt(getStringOrDefault(recordMap, "KT", DEFAULT_UNKNOWN));
        entity.setEmk(getDoubleOrDefault(recordMap, "EMK", 0.0));

        String kkom = getStringOrDefault(recordMap, "KKOM", DEFAULT_UNKNOWN);
        if (kkom.length() > 10) {
            kkom = kkom.substring(0, 10);
        }
        entity.setKkom(kkom);

        entity.setKol1t(getDoubleOrDefault(recordMap, "KOL1T", 0.0));
        entity.setKolvk(getDoubleOrDefault(recordMap, "KOLVK", 0.0));

        return entity;
    }

    private PlrMt mapToMt(Map<String, Object> recordMap) {
        PlrMt entity = new PlrMt();

        entity.setKgr(getStringOrDefault(recordMap, "KGR", DEFAULT_EMPTY));
        entity.setKmt(getStringOrDefault(recordMap, "KMT", DEFAULT_EMPTY));
        entity.setSnm(getStringOrDefault(recordMap, "SNM", DEFAULT_EMPTY));
        entity.setEdu(getStringOrDefault(recordMap, "EDU", DEFAULT_EMPTY));
        entity.setPers(getDoubleOrDefault(recordMap, "PERS", 0.0));
        entity.setRnd(getDoubleOrDefault(recordMap, "RND", 0.0));

        return entity;
    }

    private PlrPp mapToPp(Map<String, Object> recordMap) {
        PlrPp entity = new PlrPp();

        entity.setKpp(getStringOrDefault(recordMap, "KPP", DEFAULT_EMPTY));
        entity.setSnm(getStringOrDefault(recordMap, "SNM", DEFAULT_EMPTY));

        return entity;
    }

    // ==================== HELPER METHODS ====================

    private String findMemoFile(String dbfPath) {
        String basePath = dbfPath.substring(0, dbfPath.lastIndexOf('.'));
        String dbtPath = basePath + ".DBT";
        String fptPath = basePath + ".FPT";

        java.io.File dbtFile = new java.io.File(dbtPath);
        java.io.File fptFile = new java.io.File(fptPath);

        if (dbtFile.exists()) {
            return dbtPath;
        } else if (fptFile.exists()) {
            return fptPath;
        }
        return null;
    }

    private String getString(Map<String, Object> recordMap, String key) {
        Object value = recordMap.get(key);
        if (value == null) {
            return null;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }

    private String getStringOrDefault(Map<String, Object> recordMap, String key, String defaultValue) {
        String value = getString(recordMap, key);
        return value != null ? value : defaultValue;
    }

    private Double getDouble(Map<String, Object> recordMap, String key) {
        Object value = recordMap.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double getDoubleOrDefault(Map<String, Object> recordMap, String key, Double defaultValue) {
        Double value = getDouble(recordMap, key);
        return value != null ? value : defaultValue;
    }

    private Integer getInteger(Map<String, Object> recordMap, String key) {
        Object value = recordMap.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getIntegerOrDefault(Map<String, Object> recordMap, String key, Integer defaultValue) {
        Integer value = getInteger(recordMap, key);
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("unused")
    private BigDecimal getBigDecimal(Map<String, Object> recordMap, String key) {
        Object value = recordMap.get(key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate getDate(Map<String, Object> recordMap, String key) {
        Object value = recordMap.get(key);
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof java.util.Date date) {
                return date.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
            String str = value.toString().trim();
            return LocalDate.parse(str, DBF_DATE_FORMAT);
        } catch (Exception e) {
            log.warn("Failed to parse date: {} for field {}", value, key);
            return null;
        }
    }

    private LocalDate getDateOrDefault(Map<String, Object> recordMap, String key, LocalDate defaultValue) {
        LocalDate value = getDate(recordMap, key);
        return value != null ? value : defaultValue;
    }
}