package org.acme.foodpackaging.service.materials;

import com.linuxense.javadbf.DBFRow;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.entity.materials.PlrPp;
import org.acme.foodpackaging.entity.materials.PlrRnpp;
import org.acme.foodpackaging.entity.materials.PlrSprog;
import org.acme.foodpackaging.service.materials.config.MtService;
import org.acme.foodpackaging.service.materials.config.PpService;
import org.acme.foodpackaging.service.materials.config.RnppService;
import org.acme.foodpackaging.service.materials.config.SprogService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Clock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.file.Path;
import java.nio.file.Files;

@Slf4j
@ApplicationScoped
public class DbfImportService {

    private static final int BATCH_SIZE = 100;
    private static final String DEFAULT_UNKNOWN = "UNKNOWN";
    private static final String DEFAULT_EMPTY = "";
    private static final double SYSN_NONE = -1d;

    private final DbfReaderService dbfReaderService;
    private final EntityManager entityManager;
    private final MtService mtService;
    private final PpService ppService;
    private final SprogService sprogService;
    private final RnppService rnppService;
    private final Clock clock;
    private final Path importDirectory;

    private static final String SPROG_FILE = "BD_SPROG.DBF";
    private static final String RNPP_FILE = "BD_RNPP.DBF";
    private static final String MT_FILE = "NS_MT.DBF";
    private static final String PP_FILE = "NS_PP.DBF";

    @Inject
    public DbfImportService(DbfReaderService dbfReaderService, EntityManager entityManager,
                            MtService mtService, PpService ppService,
                            SprogService sprogService, RnppService rnppService,
                            @ConfigProperty(name = "dbf.import.directory") Path importDirectory) {
        this.dbfReaderService = dbfReaderService;
        this.entityManager = entityManager;
        this.mtService = mtService;
        this.ppService = ppService;
        this.sprogService = sprogService;
        this.rnppService = rnppService;
        this.importDirectory = importDirectory;
        this.clock = Clock.systemDefaultZone();
    }

    // ==================== PUBLIC IMPORT METHODS ====================

    @Transactional
    @TransactionConfiguration(timeout = 1800)
    public void importSprog() {

        Path dbfFile = getImportFile(SPROG_FILE);
        log.info("=== START SPROG IMPORT ===");
        long startTime = System.currentTimeMillis();

        long deleteStart = System.currentTimeMillis();
        sprogService.deleteAll();
        long deleteMs = System.currentTimeMillis() - deleteStart;

        AtomicInteger importedCount = new AtomicInteger(0);
        AtomicLong saveMs = new AtomicLong(0);
        List<PlrSprog> batch = new ArrayList<>(BATCH_SIZE);

        long readStart = System.currentTimeMillis();

        try {
            dbfReaderService.readDbfFileStreaming(dbfFile.toString(), row -> {
                try {
                    PlrSprog entity = mapToSprog(row);
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        long s = System.currentTimeMillis();
                        int saved = saveSprogBatch(batch);
                        saveMs.addAndGet(System.currentTimeMillis() - s);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.warn("Failed to map SPROG record", e);
                }
            });

            if (!batch.isEmpty()) {
                long s = System.currentTimeMillis();
                int saved = saveSprogBatch(batch);
                saveMs.addAndGet(System.currentTimeMillis() - s);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long readMs = System.currentTimeMillis() - readStart;
            long totalMs = System.currentTimeMillis() - startTime;
            log.info("=== FINISHED SPROG IMPORT: {} records in {} sec | delete={} ms, read={} ms (save={} ms, pure-read={} ms), total={} ms ===",
                    importedCount.get(), totalMs / 1000,
                    deleteMs, readMs, saveMs.get(), readMs - saveMs.get(), totalMs);

        } catch (Exception e) {
            log.error("SPROG import failed", e);
            throw new RuntimeException("Failed to import SPROG", e);
        }
    }

    @Transactional()
    @TransactionConfiguration(timeout = 1800)
    public void importRnpp() {

        double sysnNowMonth = sprogService.sysnNow();
        double sysnNextMonth = Optional.ofNullable(sprogService.sysnNextOrNull()).orElse(SYSN_NONE);

        Path dbfFile = getImportFile(RNPP_FILE);
        log.info("=== START Rnpp IMPORT ===");
        long startTime = System.currentTimeMillis();

        long deleteStart = System.currentTimeMillis();
        rnppService.deleteBySysn(sysnNowMonth);
        if (sysnNextMonth != SYSN_NONE) {
            rnppService.deleteBySysn(sysnNextMonth);
        }
        long deleteMs = System.currentTimeMillis() - deleteStart;

        AtomicInteger importedCount = new AtomicInteger(0);
        AtomicLong saveMs = new AtomicLong(0);
        List<PlrRnpp> batch = new ArrayList<>(BATCH_SIZE);

        long readStart = System.currentTimeMillis();

        try {

            dbfReaderService.readDbfFileStreaming(dbfFile.toString(), row -> {
                try {
                    if (row.getInt("SYSN") != sysnNowMonth && row.getInt("SYSN") != sysnNextMonth) {
                        return;
                    }

                    PlrRnpp entity = mapToRnpp(row);
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        long s = System.currentTimeMillis();
                        int saved = saveRnppBatch(batch);
                        saveMs.addAndGet(System.currentTimeMillis() - s);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to map Rnpp record", e);
                }
            });

            if (!batch.isEmpty()) {
                long s = System.currentTimeMillis();
                int saved = saveRnppBatch(batch);
                saveMs.addAndGet(System.currentTimeMillis() - s);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long readMs = System.currentTimeMillis() - readStart;
            long totalMs = System.currentTimeMillis() - startTime;
            log.info("=== FINISHED Rnpp IMPORT: {} records in {} sec | delete={} ms, read={} ms (save={} ms, pure-read={} ms), total={} ms ===",
                    importedCount.get(), totalMs / 1000,
                    deleteMs, readMs, saveMs.get(), readMs - saveMs.get(), totalMs);

        } catch (Exception e) {
            log.error("Rnpp import failed", e);
            throw new RuntimeException("Failed to import Rnpp", e);
        }
    }

    @Transactional
    @TransactionConfiguration(timeout = 1800)
    public void importMt() {
        Path dbfFile = getImportFile(MT_FILE);
        log.info("=== START Mt IMPORT ===");
        long startTime = System.currentTimeMillis();

        long loadStart = System.currentTimeMillis();
        Map<String, PlrMt> existingMap = mtService.findAllAsMapByKmt();
        long loadMs = System.currentTimeMillis() - loadStart;

        List<PlrMt> allEntitiesToSave = new ArrayList<>();
        long saveMs = 0;

        long readStart = System.currentTimeMillis();

        try {
            dbfReaderService.readDbfFileStreaming(dbfFile.toString(), row -> {
                try {
                    PlrMt newEntity = mapToMt(row);
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
                long s = System.currentTimeMillis();
                saveMtBatch(allEntitiesToSave);
                saveMs = System.currentTimeMillis() - s;
            }

            mtService.invalidateAll();

            long readMs = System.currentTimeMillis() - readStart;
            long totalMs = System.currentTimeMillis() - startTime;
            log.info("=== FINISHED Mt IMPORT: {} records in {} sec | loadExisting={} ms, read={} ms (save={} ms, pure-read={} ms), total={} ms ===",
                    allEntitiesToSave.size(), totalMs / 1000,
                    loadMs, readMs, saveMs, readMs - saveMs, totalMs);

        } catch (Exception e) {
            log.error("Mt import failed", e);
            throw new RuntimeException("Failed to import Mt", e);
        }
    }

    @Transactional
    @TransactionConfiguration(timeout = 1800)
    public void importPp() {
        Path dbfFile = getImportFile(PP_FILE);
        log.info("=== START Pp IMPORT ===");
        long startTime = System.currentTimeMillis();

        long deleteStart = System.currentTimeMillis();
        ppService.deleteAll();
        long deleteMs = System.currentTimeMillis() - deleteStart;

        AtomicInteger importedCount = new AtomicInteger(0);
        AtomicLong saveMs = new AtomicLong(0);
        List<PlrPp> batch = new ArrayList<>(BATCH_SIZE);

        long readStart = System.currentTimeMillis();

        try {
            dbfReaderService.readDbfFileStreaming(dbfFile.toString(), row -> {
                try {
                    PlrPp entity = mapToPp(row);
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        long s = System.currentTimeMillis();
                        int saved = savePpBatch(batch);
                        saveMs.addAndGet(System.currentTimeMillis() - s);
                        importedCount.addAndGet(saved);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to map Pp record", e);
                }
            });

            if (!batch.isEmpty()) {
                long s = System.currentTimeMillis();
                int saved = savePpBatch(batch);
                saveMs.addAndGet(System.currentTimeMillis() - s);
                importedCount.addAndGet(saved);
                batch.clear();
            }

            long readMs = System.currentTimeMillis() - readStart;
            long totalMs = System.currentTimeMillis() - startTime;
            log.info("=== FINISHED Pp IMPORT: {} records in {} sec | delete={} ms, read={} ms (save={} ms, pure-read={} ms), total={} ms ===",
                    importedCount.get(), totalMs / 1000,
                    deleteMs, readMs, saveMs.get(), readMs - saveMs.get(), totalMs);

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

        for (PlrSprog entity : batch) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();

        return batch.size();
    }

    private int saveRnppBatch(List<PlrRnpp> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        for (PlrRnpp entity : batch) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();

        return batch.size();
    }

    private void saveMtBatch(List<PlrMt> batch) {
        if (batch.isEmpty()) {
            return;
        }

        for (PlrMt entity : batch) {
            if (!entityManager.contains(entity)) {
                entityManager.persist(entity);   // новые — INSERT
            }
            // existing уже managed — ничего не делаем, flush подхватит изменения
        }

        entityManager.flush();
        entityManager.clear();
    }

    private int savePpBatch(List<PlrPp> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        for (PlrPp entity : batch) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();

        return batch.size();
    }

    // ==================== MAPPER METHODS ====================

    private PlrSprog mapToSprog(DBFRow row) {
        PlrSprog entity = new PlrSprog();

        entity.setSysn(row.getDouble("SYSN"));
        entity.setDt1(toLocalDate(row.getDate("DT1"), LocalDate.now(clock)));
        entity.setDt2(toLocalDate(row.getDate("DT2"), LocalDate.now(clock).plusDays(1)));
        entity.setObj(getString(row, "OBJ", DEFAULT_UNKNOWN));
        entity.setNp(row.getInt("NP"));

        return entity;
    }

    private PlrRnpp mapToRnpp(DBFRow row) {
        PlrRnpp entity = new PlrRnpp();

        entity.setSysn(row.getDouble("SYSN"));
        entity.setKmc(getString(row, "KMC", DEFAULT_UNKNOWN));
        entity.setKt(getString(row, "KT", DEFAULT_UNKNOWN));
        entity.setEmk(row.getDouble("EMK"));
        entity.setTkom(row.getInt("TKOM"));

        String kkom = getString(row, "KKOM", DEFAULT_UNKNOWN);
        if (kkom.length() > 10) {
            kkom = kkom.substring(0, 10);
        }
        entity.setKkom(kkom);

        entity.setKol1t(row.getDouble("KOL1T"));
        entity.setKolvk(row.getDouble("KOLVK"));

        return entity;
    }

    private PlrMt mapToMt(DBFRow row) {
        PlrMt entity = new PlrMt();

        entity.setKgr(getString(row, "KGR", DEFAULT_EMPTY));
        entity.setKmt(getString(row, "KMT", DEFAULT_EMPTY));
        entity.setSnm(getString(row, "SNM", DEFAULT_EMPTY));
        entity.setEdu(getString(row, "EDU", DEFAULT_EMPTY));

        return entity;
    }

    private PlrPp mapToPp(DBFRow row) {
        PlrPp entity = new PlrPp();

        entity.setKpp(getString(row, "KPP", DEFAULT_EMPTY));
        entity.setSnm(getString(row, "SNM", DEFAULT_EMPTY));

        return entity;
    }

    // ==================== HELPER METHODS ====================

    private String getString(DBFRow row, String field, String defaultValue) {
        try {
            String s = row.getString(field);
            if (s == null) {
                return defaultValue;
            }
            s = s.trim();
            return s.isEmpty() ? defaultValue : s;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private LocalDate toLocalDate(java.util.Date date, LocalDate defaultValue) {
        if (date == null) {
            return defaultValue;
        }
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private Path getImportFile(String fileName) {
        Path importRoot = importDirectory.toAbsolutePath().normalize();
        Path dbfFile = importRoot.resolve(fileName).normalize();

        if (!dbfFile.startsWith(importRoot)) {
            throw new IllegalArgumentException("Invalid DBF import file");
        }

        if (!Files.isRegularFile(dbfFile) || !Files.isReadable(dbfFile)) {
            throw new IllegalArgumentException("DBF import file is unavailable");
        }

        return dbfFile;
    }
}