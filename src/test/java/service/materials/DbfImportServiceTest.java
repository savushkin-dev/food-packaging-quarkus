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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbfImportServiceTest {

    @InjectMocks
    private DbfImportService dbfImportService;

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

    @TempDir
    java.nio.file.Path tempDir;

    private final String testDbfPath = "C:/test/file.dbf";

    // ==================== ТЕСТЫ importSprog() ====================

    @Test
    void testImportSprog_Success() {
        List<Map<String, Object>> records = createSprogRecords(5);

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(1);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        doNothing().when(sprogService).deleteAll();
        doNothing().when(entityManager).persist(any(PlrSprog.class));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();

        dbfImportService.importSprog(testDbfPath);

        verify(sprogService, times(1)).deleteAll();
        verify(entityManager, times(5)).persist(any(PlrSprog.class));
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }

    @Test
    void testImportSprog_EmptyFile() {
        doAnswer(invocation -> null)
                .when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        doNothing().when(sprogService).deleteAll();

        dbfImportService.importSprog(testDbfPath);

        verify(sprogService, times(1)).deleteAll();
        verify(entityManager, never()).persist(any(PlrSprog.class));
    }

    @Test
    void testImportSprog_WithException() {
        doThrow(new RuntimeException("DBF read error"))
                .when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        doNothing().when(sprogService).deleteAll();

        assertThrows(RuntimeException.class, () -> {
            dbfImportService.importSprog(testDbfPath);
        });
    }

    // ==================== ТЕСТЫ importRnpp() ====================

    @Test
    void testImportRnpp_Success() {
        List<Map<String, Object>> records = createRnppRecords();

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(3);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(
                eq(testDbfPath), any(), eq("CP866"), any());

        doNothing().when(rnppService).deleteAll();
        doNothing().when(entityManager).persist(any(PlrRnpp.class));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();

        dbfImportService.importRnpp(testDbfPath);

        verify(rnppService, times(1)).deleteAll();
        verify(entityManager, times(5)).persist(any(PlrRnpp.class));
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
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

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(3);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(
                eq(testDbfPath), any(), eq("CP866"), any());

        doNothing().when(rnppService).deleteAll();
        doNothing().when(entityManager).persist(any(PlrRnpp.class));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();

        dbfImportService.importRnpp(testDbfPath);

        verify(entityManager, times(1)).persist(any(PlrRnpp.class));
    }

    @Test
    void testImportRnpp_WithException() {
        doThrow(new RuntimeException("DBF read error"))
                .when(dbfReaderService).readDbfFileStreaming(
                        eq(testDbfPath), any(), eq("CP866"), any());

        doNothing().when(rnppService).deleteAll();

        assertThrows(RuntimeException.class, () -> {
            dbfImportService.importRnpp(testDbfPath);
        });
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

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(1);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        when(entityManager.merge(any(PlrMt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();
        doNothing().when(mtService).invalidateAll();

        dbfImportService.importMt(testDbfPath);

        verify(mtService, times(1)).findAllAsMapByKmt();

        ArgumentCaptor<PlrMt> captor = ArgumentCaptor.forClass(PlrMt.class);
        verify(entityManager, times(2)).merge(captor.capture());

        List<PlrMt> mergedEntities = captor.getAllValues();
        PlrMt updated = mergedEntities.get(0);

        assertEquals("New Name", updated.getSnm());
        assertEquals("pcs", updated.getEdu());
        assertEquals(15.0, updated.getPers());
        assertEquals(3.0, updated.getRnd());

        verify(mtService, times(1)).invalidateAll();
    }

    @Test
    void testImportMt_InsertNew_Success() {
        List<Map<String, Object>> records = createMtRecords();

        when(mtService.findAllAsMapByKmt()).thenReturn(new HashMap<>());

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(1);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        when(entityManager.merge(any(PlrMt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();
        doNothing().when(mtService).invalidateAll();

        dbfImportService.importMt(testDbfPath);

        ArgumentCaptor<PlrMt> captor = ArgumentCaptor.forClass(PlrMt.class);
        verify(entityManager, times(2)).merge(captor.capture());

        List<PlrMt> mergedEntities = captor.getAllValues();
        PlrMt newEntity = mergedEntities.get(0);

        assertEquals("MT000", newEntity.getKmt());
        assertEquals("Material 0", newEntity.getSnm());
        assertEquals("pcs", newEntity.getEdu());
        assertEquals(20.5, newEntity.getPers());
        assertEquals(5.0, newEntity.getRnd());

        verify(mtService, times(1)).invalidateAll();
    }

    @Test
    void testImportMt_WithException() {
        when(mtService.findAllAsMapByKmt()).thenReturn(new HashMap<>());
        doThrow(new RuntimeException("DBF read error"))
                .when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        assertThrows(RuntimeException.class, () -> {
            dbfImportService.importMt(testDbfPath);
        });
    }

    // ==================== ТЕСТЫ importPp() ====================

    @Test
    void testImportPp_Success() {
        List<Map<String, Object>> records = createPpRecords(3);

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(1);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        doNothing().when(ppService).deleteAll();
        doNothing().when(entityManager).persist(any(PlrPp.class));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();

        dbfImportService.importPp(testDbfPath);

        verify(ppService, times(1)).deleteAll();
        verify(entityManager, times(3)).persist(any(PlrPp.class));
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }

    @Test
    void testImportPp_WithException() {
        doThrow(new RuntimeException("DBF read error"))
                .when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        doNothing().when(ppService).deleteAll();

        assertThrows(RuntimeException.class, () -> {
            dbfImportService.importPp(testDbfPath);
        });
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
    void testFindMemoFile_ShouldReturnNullForInvalidPath() throws Exception {
        // Используем рефлексию для доступа к приватному методу
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod("findMemoFile", String.class);
        method.setAccessible(true);

        // Тест с null
        String result1 = (String) method.invoke(dbfImportService, (String) null);
        assertNull(result1);

        // Тест с пустым путем
        String result2 = (String) method.invoke(dbfImportService, "");
        assertNull(result2);

        // Тест с невалидным расширением
        String result3 = (String) method.invoke(dbfImportService, "test.txt");
        assertNull(result3);
    }

    @Test
    void testImportSprog_BatchSizeTriggersMultipleFlushes() {
        // 25 записей -> первый flush на 20-й, второй на оставшихся 5
        List<Map<String, Object>> records = createSprogRecords(25);

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(1);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        doNothing().when(sprogService).deleteAll();
        doNothing().when(entityManager).persist(any(PlrSprog.class));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();

        dbfImportService.importSprog(testDbfPath);

        verify(entityManager, times(25)).persist(any(PlrSprog.class));
        // один flush внутри цикла (на 20-й записи) + один после цикла на оставшихся 5
        verify(entityManager, times(2)).flush();
        verify(entityManager, times(2)).clear();
    }

    @Test
    void testImportPp_BatchSizeTriggersMultipleFlushes() {
        List<Map<String, Object>> records = createPpRecords(23);

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(1);
            for (Map<String, Object> recordMap : records) {
                consumer.accept(recordMap);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any());

        doNothing().when(ppService).deleteAll();
        doNothing().when(entityManager).persist(any(PlrPp.class));
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();

        dbfImportService.importPp(testDbfPath);

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
    void testFindMemoFile_ReturnsDbtWhenDbtExists() throws Exception {
        java.nio.file.Path dbf = tempDir.resolve("data.dbf");
        java.nio.file.Files.createFile(dbf);
        java.nio.file.Path dbt = tempDir.resolve("data.DBT");
        java.nio.file.Files.createFile(dbt);

        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod("findMemoFile", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(dbfImportService, dbf.toString());

        assertEquals(dbt.toString(), result);
    }

    @Test
    void testFindMemoFile_ReturnsFptWhenOnlyFptExists() throws Exception {
        java.nio.file.Path dbf = tempDir.resolve("data2.dbf");
        java.nio.file.Files.createFile(dbf);
        java.nio.file.Path fpt = tempDir.resolve("data2.FPT");
        java.nio.file.Files.createFile(fpt);

        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod("findMemoFile", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(dbfImportService, dbf.toString());

        assertEquals(fpt.toString(), result);
    }

    @Test
    void testFindMemoFile_ReturnsNullWhenNoMemoFileExists() throws Exception {
        java.nio.file.Path dbf = tempDir.resolve("data3.dbf");
        java.nio.file.Files.createFile(dbf);

        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod("findMemoFile", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(dbfImportService, dbf.toString());

        assertNull(result);
    }

    @Test
    void testFindMemoFile_ReturnsNullForPathWithInvalidCharacters() throws Exception {
        java.lang.reflect.Method method = DbfImportService.class.getDeclaredMethod("findMemoFile", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(dbfImportService, "bad path with spaces!.dbf");

        assertNull(result);
    }

    @Test
    void testImportRnpp_TruncatesLongKkom() {
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("SYSN", 39001);
        recordMap.put("KMC", "TEST001");
        recordMap.put("KT", "KT001");
        recordMap.put("EMK", 18.0);
        recordMap.put("KKOM", "VERYLONGKKOMVALUE"); // 17 символов, должно обрезаться до 10
        recordMap.put("KOL1T", 10.0);
        recordMap.put("KOLVK", 5.0);
        List<Map<String, Object>> records = List.of(recordMap);

        doAnswer(invocation -> {
            java.util.function.Consumer<Map<String, Object>> consumer = invocation.getArgument(3);
            for (Map<String, Object> r : records) {
                consumer.accept(r);
            }
            return null;
        }).when(dbfReaderService).readDbfFileStreaming(eq(testDbfPath), any(), eq("CP866"), any());

        doNothing().when(rnppService).deleteAll();
        ArgumentCaptor<PlrRnpp> captor = ArgumentCaptor.forClass(PlrRnpp.class);
        doNothing().when(entityManager).persist(captor.capture());
        doNothing().when(entityManager).flush();
        doNothing().when(entityManager).clear();

        dbfImportService.importRnpp(testDbfPath);

        assertEquals("VERYLONGKK", captor.getValue().getKkom()); // ровно 10 символов
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

}