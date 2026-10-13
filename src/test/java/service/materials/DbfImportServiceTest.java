package service.materials;

import com.linuxense.javadbf.DBFRow;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.entity.materials.PlrPp;
import org.acme.foodpackaging.entity.materials.PlrRnpp;
import org.acme.foodpackaging.entity.materials.PlrSprog;
import org.acme.foodpackaging.service.materials.DbfImportService;
import org.acme.foodpackaging.service.materials.DbfReaderService;
import org.acme.foodpackaging.service.materials.config.MtService;
import org.acme.foodpackaging.service.materials.config.PpService;
import org.acme.foodpackaging.service.materials.config.RnppService;
import org.acme.foodpackaging.service.materials.config.SprogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbfImportServiceTest {

    @Mock private DbfReaderService dbfReaderService;
    @Mock private EntityManager entityManager;
    @Mock private MtService mtService;
    @Mock private PpService ppService;
    @Mock private SprogService sprogService;
    @Mock private RnppService rnppService;

    private DbfImportService dbfImportService;

    @TempDir
    Path tempDir;

    private Path sprogFile;
    private Path rnppFile;
    private Path mtFile;
    private Path ppFile;

    private static final int SYSN_NOW  = 39333;
    private static final int SYSN_NEXT = 39334;

    @BeforeEach
    void setUp() throws IOException {
        sprogFile = Files.createFile(tempDir.resolve("BD_SPROG.DBF"));
        rnppFile  = Files.createFile(tempDir.resolve("BD_RNPP.DBF"));
        mtFile    = Files.createFile(tempDir.resolve("NS_MT.DBF"));
        ppFile    = Files.createFile(tempDir.resolve("NS_PP.DBF"));

        dbfImportService = new DbfImportService(
                dbfReaderService,
                entityManager,
                mtService,
                ppService,
                sprogService,
                rnppService,
                tempDir);
    }

    // ==================== DBFRow factory ====================

    /**
     * Mock DBFRow с lenient-стабами.
     * Для числовых полей стабим сразу getInt + getDouble, потому что
     * в importRnpp фильтр зовёт getInt("SYSN"), а mapToRnpp — getDouble("SYSN").
     */
    private DBFRow fakeRow(Map<String, Object> values) {
        DBFRow row = mock(DBFRow.class, withSettings().lenient());
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String field = e.getKey();
            Object val = e.getValue();
            if (val == null) {
                lenient().when(row.getString(field)).thenReturn(null);
                lenient().when(row.getDouble(field)).thenReturn(0d);
                lenient().when(row.getInt(field)).thenReturn(0);
                lenient().when(row.getDate(field)).thenReturn(null);
            } else if (val instanceof String s) {
                lenient().when(row.getString(field)).thenReturn(s);
            } else if (val instanceof Double d) {
                lenient().when(row.getDouble(field)).thenReturn(d);
                lenient().when(row.getInt(field)).thenReturn(d.intValue());
            } else if (val instanceof Integer i) {
                lenient().when(row.getInt(field)).thenReturn(i);
                lenient().when(row.getDouble(field)).thenReturn(i.doubleValue());
            } else if (val instanceof java.sql.Date d) {
                lenient().when(row.getDate(field)).thenReturn(d);
            } else if (val instanceof java.util.Date d) {
                lenient().when(row.getDate(field)).thenReturn(d);
            } else {
                lenient().when(row.getObject(field)).thenReturn(val);
            }
        }
        return row;
    }

    /**
     * Настраивает мок dbfReaderService, чтобы он отдал consumer'у переданные строки.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void simulateRead(Path file, List<Map<String, Object>> records) {
        doAnswer(invocation -> {
            Consumer<DBFRow> consumer = invocation.getArgument(1);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(fakeRow(recordMap));
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(
                eq(file.toAbsolutePath().normalize().toString()),
                any(Consumer.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void simulateReadFailure(Path file) {
        doThrow(new RuntimeException("DBF read error"))
                .when(dbfReaderService)
                .readDbfFileStreaming(
                        eq(file.toAbsolutePath().normalize().toString()),
                        any(Consumer.class));
    }

    // ==================== SPROG ====================

    @Test
    void testImportSprog_Success() {
        simulateRead(sprogFile, createSprogRecords(5));

        dbfImportService.importSprog();

        verify(sprogService).deleteAll();
        verify(entityManager, times(5)).persist(any(PlrSprog.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void testImportSprog_EmptyFile() {
        simulateRead(sprogFile, List.of());

        dbfImportService.importSprog();

        verify(sprogService).deleteAll();
        verify(entityManager, never()).persist(any(PlrSprog.class));
        verify(entityManager, never()).flush();
        verify(entityManager, never()).clear();
    }

    @Test
    void testImportSprog_WithException() {
        simulateReadFailure(sprogFile);

        assertThrows(RuntimeException.class, () -> dbfImportService.importSprog());

        verify(sprogService).deleteAll();
    }

    @Test
    void testImportSprog_BatchSizeTriggersMultipleFlushes() {
        simulateRead(sprogFile, createSprogRecords(250));

        dbfImportService.importSprog();

        verify(sprogService).deleteAll();
        verify(entityManager, times(250)).persist(any(PlrSprog.class));
        verify(entityManager, times(3)).flush();   // 100 + 100 + 50
        verify(entityManager, times(3)).clear();
    }

    @Test
    void testImportSprog_HandlesNullAndUtilDate() {
        List<Map<String, Object>> records = new ArrayList<>();

        // Запись 1: DT1 = null → toLocalDate(null, default)
        Map<String, Object> r1 = new HashMap<>();
        r1.put("SYSN", 39001.0);
        r1.put("DT1", null);
        r1.put("DT2", null);
        r1.put("OBJ", "OBJ-NULL");
        r1.put("NP", 0);
        records.add(r1);

        // Запись 2: DT1 = java.util.Date → toInstant().atZone().toLocalDate()
        Map<String, Object> r2 = new HashMap<>();
        r2.put("SYSN", 39002.0);
        r2.put("DT1", java.util.Date.from(
                LocalDate.of(2026, Month.FEBRUARY, 15)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()));
        r2.put("DT2", java.util.Date.from(
                LocalDate.of(2026, Month.MARCH, 15)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()));
        r2.put("OBJ", "OBJ-UTIL");
        r2.put("NP", 1);
        records.add(r2);

        simulateRead(sprogFile, records);

        dbfImportService.importSprog();

        ArgumentCaptor<PlrSprog> captor = ArgumentCaptor.forClass(PlrSprog.class);
        verify(entityManager, times(2)).persist(captor.capture());

        List<PlrSprog> saved = captor.getAllValues();
        assertNotNull(saved.get(0).getDt1(), "DT1=null должен дать default (today)");
        assertEquals(LocalDate.of(2026, Month.FEBRUARY, 15), saved.get(1).getDt1());
        assertEquals(LocalDate.of(2026, Month.MARCH, 15), saved.get(1).getDt2());
    }

    // ==================== RNPP ====================

    @Test
    void testImportRnpp_Success() {
        when(sprogService.sysnNow()).thenReturn((double) SYSN_NOW);
        when(sprogService.sysnNextOrNull()).thenReturn(null);

        simulateRead(rnppFile, createRnppRecords(SYSN_NOW, 5));

        dbfImportService.importRnpp();

        verify(rnppService).deleteBySysn((double) SYSN_NOW);
        verify(entityManager, times(5)).persist(any(PlrRnpp.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void testImportRnpp_FiltersBySysn() {
        when(sprogService.sysnNow()).thenReturn((double) SYSN_NOW);
        when(sprogService.sysnNextOrNull()).thenReturn(null);

        List<Map<String, Object>> records = new ArrayList<>();
        records.add(rnppRecord(99999, "KMC-NO"));          // не подходит
        records.add(rnppRecord(SYSN_NOW, "KMC-OK"));       // подходит

        simulateRead(rnppFile, records);

        dbfImportService.importRnpp();

        verify(rnppService).deleteBySysn((double) SYSN_NOW);
        verify(entityManager, times(1)).persist(any(PlrRnpp.class));
    }

    @Test
    void testImportRnpp_WithException() {
        when(sprogService.sysnNow()).thenReturn((double) SYSN_NOW);
        when(sprogService.sysnNextOrNull()).thenReturn(null);

        simulateReadFailure(rnppFile);

        assertThrows(RuntimeException.class, () -> dbfImportService.importRnpp());

        verify(rnppService).deleteBySysn((double) SYSN_NOW);
    }

    @Test
    void testImportRnpp_BatchSizeTriggersMultipleFlushes() {
        when(sprogService.sysnNow()).thenReturn((double) SYSN_NOW);
        when(sprogService.sysnNextOrNull()).thenReturn(null);

        simulateRead(rnppFile, createRnppRecords(SYSN_NOW, 250));

        dbfImportService.importRnpp();

        verify(rnppService).deleteBySysn((double) SYSN_NOW);
        verify(entityManager, times(250)).persist(any(PlrRnpp.class));
        verify(entityManager, times(3)).flush();   // 100 + 100 + 50
        verify(entityManager, times(3)).clear();
    }

    @Test
    void testImportRnpp_DeletesBothCurrentAndNextMonths() {
        when(sprogService.sysnNow()).thenReturn((double) SYSN_NOW);
        when(sprogService.sysnNextOrNull()).thenReturn((double) SYSN_NEXT);

        simulateRead(rnppFile, List.of());

        dbfImportService.importRnpp();

        verify(rnppService).deleteBySysn((double) SYSN_NOW);
        verify(rnppService).deleteBySysn((double) SYSN_NEXT);
    }

    @Test
    void testImportRnpp_TruncatesLongKkom() {
        when(sprogService.sysnNow()).thenReturn((double) SYSN_NOW);
        when(sprogService.sysnNextOrNull()).thenReturn(null);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("SYSN", SYSN_NOW);
        recordMap.put("KMC", "TEST001");
        recordMap.put("KT", "KT001");
        recordMap.put("EMK", 18.0);
        recordMap.put("KKOM", "VERYLONGKKOMVALUE");
        recordMap.put("KOL1T", 10.0);
        recordMap.put("KOLVK", 5.0);

        simulateRead(rnppFile, List.of(recordMap));

        dbfImportService.importRnpp();

        ArgumentCaptor<PlrRnpp> captor = ArgumentCaptor.forClass(PlrRnpp.class);
        verify(entityManager).persist(captor.capture());
        assertEquals("VERYLONGKK", captor.getValue().getKkom());
    }

    // ==================== MT ====================

    @Test
    void testImportMt_UpdateExisting_Success() {
        List<Map<String, Object>> records = createMtRecordsForUpdate();

        PlrMt existing = new PlrMt();
        existing.setKmt("MT000");
        existing.setKgr("GRP0");
        existing.setSnm("Old Name");
        existing.setEdu("kg");
        existing.setPers(15.0);
        existing.setRnd(3.0);

        Map<String, PlrMt> existingMap = new HashMap<>();
        existingMap.put("MT000", existing);
        when(mtService.findAllAsMapByKmt()).thenReturn(existingMap);

        simulateRead(mtFile, records);

        dbfImportService.importMt();

        verify(mtService).findAllAsMapByKmt();
        verify(mtService).invalidateAll();

        // existing обновился по kgr/snm/edu
        assertEquals("New Name", existing.getSnm());
        assertEquals("pcs", existing.getEdu());

        // pers и rnd не тронуты — их нет в DBF
        assertEquals(15.0, existing.getPers());
        assertEquals(3.0, existing.getRnd());

        // Новый материал (MT001) → persist
        verify(entityManager, atLeastOnce()).persist(any(PlrMt.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void testImportMt_InsertNew_Success() {
        when(mtService.findAllAsMapByKmt()).thenReturn(new HashMap<>());

        simulateRead(mtFile, createMtRecords());

        dbfImportService.importMt();

        ArgumentCaptor<PlrMt> captor = ArgumentCaptor.forClass(PlrMt.class);
        verify(entityManager, times(2)).persist(captor.capture());

        List<PlrMt> captured = captor.getAllValues();
        assertEquals("MT000", captured.get(0).getKmt());
        assertEquals("Material 0", captured.get(0).getSnm());
        assertEquals("pcs", captured.get(0).getEdu());

        verify(entityManager).flush();
        verify(entityManager).clear();
        verify(mtService).invalidateAll();
    }

    @Test
    void testImportMt_WithException() {
        when(mtService.findAllAsMapByKmt()).thenReturn(new HashMap<>());

        simulateReadFailure(mtFile);

        assertThrows(RuntimeException.class, () -> dbfImportService.importMt());

        verify(mtService).findAllAsMapByKmt();
        verify(mtService, never()).invalidateAll();
    }

    /**
     * Покрывает ветку getString(...): после trim получается пустая строка → DEFAULT_EMPTY.
     */
    @Test
    void testImportMt_BlankStringsAreReplacedWithDefault() {
        when(mtService.findAllAsMapByKmt()).thenReturn(new HashMap<>());

        Map<String, Object> record = new HashMap<>();
        record.put("KGR", "GRP");
        record.put("KMT", "MT-BLANK");
        record.put("SNM", "   ");   // trim → "" → DEFAULT_EMPTY
        record.put("EDU", "   ");   // trim → "" → DEFAULT_EMPTY

        simulateRead(mtFile, List.of(record));

        dbfImportService.importMt();

        ArgumentCaptor<PlrMt> captor = ArgumentCaptor.forClass(PlrMt.class);
        verify(entityManager).persist(captor.capture());
        assertEquals("", captor.getValue().getSnm());
        assertEquals("", captor.getValue().getEdu());
    }

    /**
     * Покрывает catch-ветку в getString: mock кидает исключение на несуществующем поле.
     * В реальной жизни это DBFFieldNotFoundException.
     */
    @Test
    void testImportMt_HandlesMissingFieldInRow() {
        when(mtService.findAllAsMapByKmt()).thenReturn(new HashMap<>());

        DBFRow row = mock(DBFRow.class, withSettings().lenient());
        lenient().when(row.getString("KGR")).thenReturn("GRP");
        lenient().when(row.getString("KMT")).thenReturn("MT-MISSING");
        // SNM и EDU отсутствуют в моке → Mockito вернёт null.
        // Чтобы сработал catch, заставим getString бросить исключение:
        lenient().when(row.getString("SNM"))
                .thenThrow(new RuntimeException("DBFFieldNotFoundException"));
        lenient().when(row.getString("EDU")).thenReturn("kg");

        doAnswer(invocation -> {
            Consumer<DBFRow> consumer = invocation.getArgument(1);
            consumer.accept(row);
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(
                eq(mtFile.toAbsolutePath().normalize().toString()),
                any(Consumer.class));

        dbfImportService.importMt();

        ArgumentCaptor<PlrMt> captor = ArgumentCaptor.forClass(PlrMt.class);
        verify(entityManager).persist(captor.capture());
        assertEquals("", captor.getValue().getSnm());   // упало → DEFAULT_EMPTY
    }

    // ==================== PP ====================

    @Test
    void testImportPp_Success() {
        simulateRead(ppFile, createPpRecords(3));

        dbfImportService.importPp();

        verify(ppService).deleteAll();
        verify(entityManager, times(3)).persist(any(PlrPp.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void testImportPp_WithException() {
        simulateReadFailure(ppFile);

        assertThrows(RuntimeException.class, () -> dbfImportService.importPp());

        verify(ppService).deleteAll();
    }

    @Test
    void testImportPp_BatchSizeTriggersMultipleFlushes() {
        simulateRead(ppFile, createPpRecords(230));

        dbfImportService.importPp();

        verify(ppService).deleteAll();
        verify(entityManager, times(230)).persist(any(PlrPp.class));
        verify(entityManager, times(3)).flush();   // 100 + 100 + 30
        verify(entityManager, times(3)).clear();
    }

    // ==================== getImportFile ====================

    @Test
    void testImportSprog_ThrowsWhenFileMissing() throws IOException {
        Files.delete(sprogFile);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dbfImportService.importSprog());

        assertEquals("DBF import file is unavailable", ex.getMessage());
        verify(sprogService, never()).deleteAll();
        verify(dbfReaderService, never()).readDbfFileStreaming(anyString(), any(Consumer.class));
    }

    @Test
    void testGetImportFile_RejectsPathTraversal() throws Exception {
        var method = DbfImportService.class.getDeclaredMethod("getImportFile", String.class);
        method.setAccessible(true);

        var thrown = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(dbfImportService, "../evil.DBF"));

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertEquals("Invalid DBF import file", thrown.getCause().getMessage());
    }

    // ==================== HELPERS ====================

    private List<Map<String, Object>> createSprogRecords(int count) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("SYSN", 39000.0 + i);
            // java.sql.Date.toLocalDate() — безопасно, в отличие от toInstant()
            r.put("DT1", java.sql.Date.valueOf(LocalDate.of(2026, Month.FEBRUARY, 15)));
            r.put("DT2", java.sql.Date.valueOf(LocalDate.of(2026, Month.MARCH, 15)));
            r.put("OBJ", "OBJ00" + i);
            r.put("NP", i);
            records.add(r);
        }
        return records;
    }

    private List<Map<String, Object>> createRnppRecords(int sysn, int count) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(rnppRecord(sysn, "KMC" + i));
        }
        return records;
    }

    private Map<String, Object> rnppRecord(int sysn, String kmc) {
        Map<String, Object> r = new HashMap<>();
        r.put("SYSN", sysn);
        r.put("KMC", kmc);
        r.put("KT", "KT001");
        r.put("EMK", 18.0);
        r.put("KKOM", "MT001");
        r.put("KOL1T", 10.0);
        r.put("KOLVK", 5.0);
        return r;
    }

    private List<Map<String, Object>> createMtRecords() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("KGR", "GRP" + i);
            r.put("KMT", "MT00" + i);
            r.put("SNM", "Material " + i);
            r.put("EDU", "pcs");
            records.add(r);
        }
        return records;
    }

    private List<Map<String, Object>> createMtRecordsForUpdate() {
        List<Map<String, Object>> records = new ArrayList<>();

        Map<String, Object> r1 = new HashMap<>();
        r1.put("KGR", "GRP0");
        r1.put("KMT", "MT000");
        r1.put("SNM", "New Name");
        r1.put("EDU", "pcs");
        records.add(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("KGR", "GRP1");
        r2.put("KMT", "MT001");
        r2.put("SNM", "Material 1");
        r2.put("EDU", "kg");
        records.add(r2);

        return records;
    }

    private List<Map<String, Object>> createPpRecords(int count) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("KPP", "PP00" + i);
            r.put("SNM", "Recipient " + i);
            records.add(r);
        }
        return records;
    }
}