package service.materials;

import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.entity.materials.PlrPp;
import org.acme.foodpackaging.entity.materials.PlrRnpp;
import org.acme.foodpackaging.entity.materials.PlrSprog;
import org.acme.foodpackaging.service.materials.*;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
class DbfImportServiceTest {
    @Mock
    private DbfReaderService dbfReaderService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private MtService mtService;

    @Mock
    private PpService ppService;

    @Mock
    private SprogService sprogService;

    @Mock
    private RnppService rnppService;

    private DbfImportService dbfImportService;

    @TempDir
    Path tempDir;

    private Path sprogFile;
    private Path rnppFile;
    private Path mtFile;
    private Path ppFile;

    @BeforeEach
    void setUp() throws IOException {
        sprogFile = Files.createFile(tempDir.resolve("BD_SPROG.DBF"));
        rnppFile = Files.createFile(tempDir.resolve("BD_RNPP.DBF"));
        mtFile = Files.createFile(tempDir.resolve("NS_MT.DBF"));
        ppFile = Files.createFile(tempDir.resolve("NS_PP.DBF"));

        dbfImportService = new DbfImportService(
                dbfReaderService,
                entityManager,
                mtService,
                ppService,
                sprogService,
                rnppService,
                tempDir);
    }

    private void simulateRead(Path file, List<Map<String, Object>> records) {
        doAnswer(invocation -> {
            Consumer<Map<String, Object>> consumer = invocation.getArgument(1);

            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }

            return null;
        }).when(dbfReaderService).readDbfFileStreaming(
                eq(file.toAbsolutePath().normalize().toString()),
                any());
    }

    private void simulateReadFailure(Path file) {
        doThrow(new RuntimeException("DBF read error"))
                .when(dbfReaderService)
                .readDbfFileStreaming(
                        eq(file.toAbsolutePath().normalize().toString()),
                        any());
    }
    // ==================== ТЕСТЫ importSprog() ====================

    @Test
    void testImportSprog_Success() {
        List<Map<String, Object>> records = createSprogRecords(5);

        simulateRead(sprogFile, records);

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

    // ==================== ТЕСТЫ importRnpp() ====================

    @Test
    void testImportRnpp_Success() {
        List<Map<String, Object>> records = createRnppRecords();

        simulateRead(rnppFile, records);

        dbfImportService.importRnpp();

        verify(rnppService).deleteAll();
        verify(entityManager, times(5)).persist(any(PlrRnpp.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void testImportRnpp_FiltersByMinSysn() {
        List<Map<String, Object>> records = new ArrayList<>();

        Map<String, Object> recordLow = new HashMap<>();
        recordLow.put("SYSN", 38000);
        recordLow.put("KMC", "TEST001");
        recordLow.put("KT", "KT001");
        recordLow.put("EMK", 18.0);
        recordLow.put("KKOM", "MT001");
        recordLow.put("KOL1T", 10.0);
        recordLow.put("KOLVK", 5.0);
        records.add(recordLow);

        Map<String, Object> recordHigh = new HashMap<>();
        recordHigh.put("SYSN", 39001);
        recordHigh.put("KMC", "TEST002");
        recordHigh.put("KT", "KT002");
        recordHigh.put("EMK", 20.0);
        recordHigh.put("KKOM", "MT002");
        recordHigh.put("KOL1T", 15.0);
        recordHigh.put("KOLVK", 8.0);
        records.add(recordHigh);

        simulateRead(rnppFile, records);

        dbfImportService.importRnpp();

        verify(rnppService).deleteAll();
        verify(entityManager, times(1)).persist(any(PlrRnpp.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void testImportRnpp_WithException() {
        simulateReadFailure(rnppFile);

        assertThrows(RuntimeException.class, () -> dbfImportService.importRnpp());

        verify(rnppService).deleteAll();
    }

    // ==================== ТЕСТЫ importMt() ====================

    @Test
    void testImportMt_UpdateExisting_Success() {
        List<Map<String, Object>> records = createMtRecordsForUpdate();

        Map<String, PlrMt> existingMap = new HashMap<>();
        PlrMt existing = new PlrMt();
        existing.setKmt("MT000");
        existing.setKgr("GRP0");
        existing.setSnm("Old Name");
        existing.setEdu("kg");
        existing.setPers(15.0);
        existing.setRnd(3.0);
        existingMap.put("MT000", existing);

        when(mtService.findAllAsMapByKmt()).thenReturn(existingMap);
        when(entityManager.merge(any(PlrMt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        simulateRead(mtFile, records);

        dbfImportService.importMt();

        verify(mtService).findAllAsMapByKmt();

        ArgumentCaptor<PlrMt> captor = ArgumentCaptor.forClass(PlrMt.class);
        verify(entityManager, times(2)).merge(captor.capture());

        PlrMt updated = captor.getAllValues().get(0);
        assertEquals("New Name", updated.getSnm());
        assertEquals("pcs", updated.getEdu());

        // При обновлении PERS и RND не должны изменяться.
        assertEquals(15.0, updated.getPers());
        assertEquals(3.0, updated.getRnd());

        verify(entityManager).flush();
        verify(entityManager).clear();
        verify(mtService).invalidateAll();
    }

    @Test
    void testImportMt_InsertNew_Success() {
        List<Map<String, Object>> records = createMtRecords();

        when(mtService.findAllAsMapByKmt()).thenReturn(new HashMap<>());
        when(entityManager.merge(any(PlrMt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        simulateRead(mtFile, records);

        dbfImportService.importMt();

        ArgumentCaptor<PlrMt> captor = ArgumentCaptor.forClass(PlrMt.class);
        verify(entityManager, times(2)).merge(captor.capture());

        PlrMt newEntity = captor.getAllValues().get(0);
        assertEquals("MT000", newEntity.getKmt());
        assertEquals("Material 0", newEntity.getSnm());
        assertEquals("pcs", newEntity.getEdu());
        assertEquals(20.5, newEntity.getPers());
        assertEquals(5.0, newEntity.getRnd());

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

    // ==================== ТЕСТЫ importPp() ====================

    @Test
    void testImportPp_Success() {
        List<Map<String, Object>> records = createPpRecords(3);

        simulateRead(ppFile, records);

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

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private List<Map<String, Object>> createSprogRecords(int count) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("SYSN", 39000.0 + i);
            recordMap.put("DT1", "2026-02-15");
            recordMap.put("DT2", "2026-03-15");
            recordMap.put("OBJ", "OBJ00" + i);
            recordMap.put("NP", i);
            records.add(recordMap);
        }
        return records;
    }

    private List<Map<String, Object>> createRnppRecords() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("SYSN", 39000.0 + i);
            recordMap.put("KMC", "KMC00" + i);
            recordMap.put("KT", "KT00" + i);
            recordMap.put("EMK", 18.0 + i);
            recordMap.put("KKOM", "MT00" + i);
            recordMap.put("KOL1T", 10.0 + i);
            recordMap.put("KOLVK", 5.0 + i);
            records.add(recordMap);
        }
        return records;
    }

    private List<Map<String, Object>> createMtRecords() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("KGR", "GRP" + i);
            recordMap.put("KMT", "MT00" + i);
            recordMap.put("SNM", "Material " + i);
            recordMap.put("EDU", "pcs");
            recordMap.put("PERS", 20.5 + i);
            recordMap.put("RND", 5.0 + i);
            records.add(recordMap);
        }
        return records;
    }

    private List<Map<String, Object>> createMtRecordsForUpdate() {
        List<Map<String, Object>> records = new ArrayList<>();
        // Первая запись - обновление существующей
        Map<String, Object> record1 = new HashMap<>();
        record1.put("KGR", "GRP0");
        record1.put("KMT", "MT000");
        record1.put("SNM", "New Name");
        record1.put("EDU", "pcs");
        record1.put("PERS", 25.0);
        record1.put("RND", 7.0);
        records.add(record1);
        // Вторая запись - новый материал
        Map<String, Object> record2 = new HashMap<>();
        record2.put("KGR", "GRP1");
        record2.put("KMT", "MT001");
        record2.put("SNM", "Material 1");
        record2.put("EDU", "kg");
        record2.put("PERS", 30.0);
        record2.put("RND", 10.0);
        records.add(record2);
        return records;
    }

    private List<Map<String, Object>> createPpRecords(int count) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("KPP", "PP00" + i);
            recordMap.put("SNM", "Recipient " + i);
            records.add(recordMap);
        }
        return records;
    }

    @Test
    void testImportSprog_BatchSizeTriggersMultipleFlushes() {
        List<Map<String, Object>> records = createSprogRecords(25);

        simulateRead(sprogFile, records);

        dbfImportService.importSprog();

        verify(sprogService).deleteAll();
        verify(entityManager, times(25)).persist(any(PlrSprog.class));
        verify(entityManager, times(2)).flush();
        verify(entityManager, times(2)).clear();
    }

    @Test
    void testImportPp_BatchSizeTriggersMultipleFlushes() {
        List<Map<String, Object>> records = createPpRecords(23);

        simulateRead(ppFile, records);

        dbfImportService.importPp();

        verify(ppService).deleteAll();
        verify(entityManager, times(23)).persist(any(PlrPp.class));
        verify(entityManager, times(2)).flush();
        verify(entityManager, times(2)).clear();
    }

    @Test
    void testGetBigDecimal_ValidAndInvalidAndNull() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod(
                "getBigDecimal", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("AMT", "123.45");
        recordMap.put("BAD", "not-a-number");

        BigDecimal valid = (BigDecimal) method.invoke(dbfImportService, recordMap, "AMT");
        assertEquals(new BigDecimal("123.45"), valid);

        BigDecimal invalid = (BigDecimal) method.invoke(dbfImportService, recordMap, "BAD");
        assertNull(invalid);

        BigDecimal missing = (BigDecimal) method.invoke(dbfImportService, recordMap, "MISSING");
        assertNull(missing);
    }

    @Test
    void testGetDate_ParsesValidStringAndJavaUtilDateAndInvalid() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod(
                "getDate", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("DT_STR", "20260215");
        recordMap.put("DT_DATE", java.util.Date.from(
                LocalDate.of(2026, Month.FEBRUARY, 15).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        recordMap.put("DT_BAD", "not-a-date");

        LocalDate fromString = (LocalDate) method.invoke(dbfImportService, recordMap, "DT_STR");
        assertEquals(LocalDate.of(2026, Month.FEBRUARY, 15), fromString);

        LocalDate fromDate = (LocalDate) method.invoke(dbfImportService, recordMap, "DT_DATE");
        assertEquals(LocalDate.of(2026, Month.FEBRUARY, 15), fromDate);

        LocalDate fromBad = (LocalDate) method.invoke(dbfImportService, recordMap, "DT_BAD");
        assertNull(fromBad);
    }

    @Test
    void testImportRnpp_TruncatesLongKkom() {
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("SYSN", 39001);
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

    @Test
    void testGetDouble_ReturnsNullOnInvalidFormat() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod(
                "getDouble", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("BAD", "not-a-number");

        Double result = (Double) method.invoke(dbfImportService, recordMap, "BAD");

        assertNull(result);
    }

    @Test
    void testGetInteger_ReturnsNullOnInvalidFormat() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod(
                "getInteger", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("BAD", "not-a-number");

        Integer result = (Integer) method.invoke(dbfImportService, recordMap, "BAD");

        assertNull(result);
    }

    @Test
    void testGetString_ReturnsNullForBlankValue() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod(
                "getString", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("BLANK", "   ");

        String result = (String) method.invoke(dbfImportService, recordMap, "BLANK");

        assertNull(result);
    }

    @Test
    void testGetStringOrDefault_UsesDefaultForMissingKey() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod(
                "getStringOrDefault", Map.class, String.class, String.class);
        method.setAccessible(true);

        Map<String, Object> recordMap = new HashMap<>();

        String result = (String) method.invoke(dbfImportService, recordMap, "MISSING", "FALLBACK");

        assertEquals("FALLBACK", result);
    }

    @Test
    void testMapToPp_MapsFieldsCorrectly() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod("mapToPp", Map.class);
        method.setAccessible(true);

        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("KPP", "PP123");
        recordMap.put("SNM", "Test Recipient");

        PlrPp result = (PlrPp) method.invoke(dbfImportService, recordMap);

        assertEquals("PP123", result.getKpp());
        assertEquals("Test Recipient", result.getSnm());
    }


    // ==================== ДОПОЛНИТЕЛЬНЫЕ ТЕСТЫ ====================

    @Test
    void testImportSprog_ThrowsWhenFileMissing() throws IOException {
        Files.delete(sprogFile);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dbfImportService.importSprog());

        assertEquals("DBF import file is unavailable", ex.getMessage());
        verify(sprogService, never()).deleteAll();
        verify(dbfReaderService, never()).readDbfFileStreaming(anyString(), any());
    }

    @Test
    void testGetImportFile_RejectsPathTraversal() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod("getImportFile", String.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException thrown = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(dbfImportService, "../evil.DBF"));

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertEquals("Invalid DBF import file", thrown.getCause().getMessage());
    }

    @Test
    void testImportRnpp_SkipsRecordOnClassCastException() {
        List<Map<String, Object>> records = new ArrayList<>();

        // SYSN не число - вызовет ClassCastException внутри лямбды, запись должна быть пропущена
        Map<String, Object> badRecord = new HashMap<>();
        badRecord.put("SYSN", "not-a-number");
        badRecord.put("KMC", "BAD001");
        badRecord.put("KT", "KT001");
        badRecord.put("EMK", 1.0);
        badRecord.put("KKOM", "MT001");
        badRecord.put("KOL1T", 1.0);
        badRecord.put("KOLVK", 1.0);
        records.add(badRecord);

        Map<String, Object> goodRecord = new HashMap<>();
        goodRecord.put("SYSN", 39005);
        goodRecord.put("KMC", "OK001");
        goodRecord.put("KT", "KT002");
        goodRecord.put("EMK", 2.0);
        goodRecord.put("KKOM", "MT002");
        goodRecord.put("KOL1T", 2.0);
        goodRecord.put("KOLVK", 2.0);
        records.add(goodRecord);

        simulateRead(rnppFile, records);

        assertDoesNotThrow(() -> dbfImportService.importRnpp());

        verify(rnppService).deleteAll();
        verify(entityManager, times(1)).persist(any(PlrRnpp.class));
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

}