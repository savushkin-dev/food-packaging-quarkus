package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.acme.foodpackaging.entity.materials.PlrRnpp;
import org.acme.foodpackaging.entity.materials.PlrSprog;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.entity.materials.PlrPp;

import java.math.BigDecimal;
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


    private final DbfReaderService dbfReaderService;
    private final EntityManager entityManager;

    @Inject
    public DbfImportService(DbfReaderService dbfReaderService, EntityManager entityManager) {
        this.dbfReaderService = dbfReaderService;
        this.entityManager = entityManager;
    }


    public int importSprog(String dbfPath) {
        log.info("=== START SPROG IMPORT ===");
        long startTime = System.currentTimeMillis();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<PlrSprog> batch = new ArrayList<>(BATCH_SIZE);

        try {
            dbfReaderService.readDbfFileStreaming(dbfPath, (Map<String, Object> record) -> {
                try {
                    PlrSprog entity = mapToSprog(record);
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
            return importedCount.get();

        } catch (Exception e) {
            log.error("SPROG import failed", e);
            throw new RuntimeException("Failed to import SPROG", e);
        }
    }


    public int importRnpp(String dbfPath) {
        log.info("=== START Rnpp IMPORT ===");
        long startTime = System.currentTimeMillis();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<PlrRnpp> batch = new ArrayList<>(BATCH_SIZE);

        try {
            String basePath = dbfPath.substring(0, dbfPath.lastIndexOf('.'));
            String dbtPath = basePath + ".DBT";
            String fptPath = basePath + ".FPT";

            String memoPath = null;
            if (new java.io.File(dbtPath).exists()) {
                memoPath = dbtPath;
            } else if (new java.io.File(fptPath).exists()) {
                memoPath = fptPath;
            }

            dbfReaderService.readDbfFileStreaming(dbfPath, memoPath, "CP866", (Map<String, Object> record) -> {
                try {
                    if (((Number) record.get("SYSN")).intValue() < 39000) {
                        return;
                    }

                    PlrRnpp entity = mapToRnpp(record);
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
            return importedCount.get();

        } catch (Exception e) {
            log.error("Rnpp import failed", e);
            throw new RuntimeException("Failed to import Rnpp", e);
        }
    }

    public int importMt(String dbfPath) {
        log.info("=== START Mt IMPORT ===");
        long startTime = System.currentTimeMillis();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<PlrMt> batch = new ArrayList<>(BATCH_SIZE);

        try {
            dbfReaderService.readDbfFileStreaming(dbfPath, (Map<String, Object> record) -> {
                try {
                    PlrMt entity = mapToMt(record);
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        int saved = saveMtBatch(batch);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to map Mt record", e);
                }
            });

            if (!batch.isEmpty()) {
                int saved = saveMtBatch(batch);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== FINISHED Mt IMPORT: {} records in {} sec ===", importedCount.get(), totalTime);
            return importedCount.get();

        } catch (Exception e) {
            log.error("Mt import failed", e);
            throw new RuntimeException("Failed to import Mt", e);
        }
    }

    public int importPp(String dbfPath) {
        log.info("=== START Pp IMPORT ===");
        long startTime = System.currentTimeMillis();

        AtomicInteger importedCount = new AtomicInteger(0);
        List<PlrPp> batch = new ArrayList<>(BATCH_SIZE);

        try {
            dbfReaderService.readDbfFileStreaming(dbfPath, (Map<String, Object> record) -> {
                try {
                    PlrPp entity = mapToPp(record);
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
            return importedCount.get();

        } catch (Exception e) {
            log.error("Pp import failed", e);
            throw new RuntimeException("Failed to import Pp", e);
        }
    }


    @Transactional
    public int saveSprogBatch(List<PlrSprog> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        for (PlrSprog entity : batch) {
            entityManager.merge(entity);
        }
        entityManager.flush();
        entityManager.clear();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Saved SPROG batch of {} records in {} ms", batch.size(), duration);

        return batch.size();
    }

    @Transactional
    public int saveRnppBatch(List<PlrRnpp> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        for (PlrRnpp entity : batch) {
            entityManager.merge(entity);
        }
        entityManager.flush();
        entityManager.clear();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Saved Rnpp batch of {} records in {} ms", batch.size(), duration);

        return batch.size();
    }

    @Transactional
    public int saveMtBatch(List<PlrMt> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        for (PlrMt entity : batch) {
            entityManager.merge(entity);
        }
        entityManager.flush();
        entityManager.clear();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Saved Mt batch of {} records in {} ms", batch.size(), duration);

        return batch.size();
    }

    @Transactional
    public int savePpBatch(List<PlrPp> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        for (PlrPp entity : batch) {
            entityManager.merge(entity);
        }
        entityManager.flush();
        entityManager.clear();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Saved Pp batch of {} records in {} ms", batch.size(), duration);

        return batch.size();
    }

    private PlrSprog mapToSprog(Map<String, Object> record) {
        PlrSprog entity = new PlrSprog();

        entity.setSysn(getDoubleOrDefault(record, "SYSN", 0.0));
        entity.setDt1(getDateOrDefault(record, "DT1", LocalDate.now()));
        entity.setDt2(getDateOrDefault(record, "DT2", LocalDate.now().plusDays(1)));
        entity.setObj(getStringOrDefault(record, "OBJ", "UNKNOWN"));
        entity.setNp(getIntegerOrDefault(record, "NP", 0));

        return entity;
    }

    private PlrRnpp mapToRnpp(Map<String, Object> record) {
        PlrRnpp entity = new PlrRnpp();

        entity.setSysn(getDoubleOrDefault(record, "SYSN", 0.0));
        entity.setKmc(getStringOrDefault(record, "KMC", "UNKNOWN"));
        entity.setKt(getStringOrDefault(record, "KT", "UNKNOWN"));
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

    private PlrMt mapToMt(Map<String, Object> record) {
        PlrMt entity = new PlrMt();

        entity.setKgr(getStringOrDefault(record, "KGR", ""));
        entity.setKmt(getStringOrDefault(record, "KMT", ""));
        entity.setSnm(getStringOrDefault(record, "SNM", ""));
        entity.setEdu(getStringOrDefault(record, "EDU", ""));
        entity.setPers(getDoubleOrDefault(record, "PERS", 0.0));
        entity.setRnd(getDoubleOrDefault(record, "RND", 0.0));

        return entity;
    }

    private PlrPp mapToPp(Map<String, Object> record) {
        PlrPp entity = new PlrPp();

        entity.setKpp(getStringOrDefault(record, "KPP", ""));
        entity.setSnm(getStringOrDefault(record, "SNM", ""));

        return entity;
    }

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

    private BigDecimal getBigDecimal(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal getBigDecimalOrDefault(Map<String, Object> record, String key, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(record, key);
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