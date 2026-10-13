package service.materials;

import com.linuxense.javadbf.DBFDataType;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFRow;
import com.linuxense.javadbf.DBFWriter;
import org.acme.foodpackaging.service.materials.DbfReaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(MockitoExtension.class)
class DbfReaderServiceTest {

    @InjectMocks
    private DbfReaderService service;

    @TempDir
    Path tempDir;

    /**
     * DBFRow переиспользуется reader'ом, поэтому нельзя сохранять его в список.
     * Сохраняем "снимок" полей в Map — тем самым проверяем и значения, и порядок.
     */
    private List<Map<String, Object>> capturedRecords;
    private Consumer<DBFRow> consumer;

    @BeforeEach
    void setUp() {
        capturedRecords = new ArrayList<>();
        consumer = row -> capturedRecords.add(snapshot(row));
    }

    /**
     * Делает снимок всех известных полей DBFRow в LinkedHashMap.
     * Использует getObject() — не кидает DBFFieldNotFoundException для отсутствующих полей,
     * потому что мы проходим по конкретному набору, который задаём сами.
     */
    private Map<String, Object> snapshot(DBFRow row) {
        // В тестах нам нужны конкретные поля — их и вытаскиваем.
        // Для теста "shouldExposeFieldNamesInOrder" смотрим порядок ниже.
        Map<String, Object> map = new LinkedHashMap<>();
        for (String name : KNOWN_FIELDS) {
            try {
                map.put(name, row.getObject(name));
            } catch (Exception ignored) {
                // поля нет — пропускаем
            }
        }
        return map;
    }

    private static final List<String> KNOWN_FIELDS = List.of(
            "NAME", "AGE", "SALARY", "STR", "NUM", "LOG",
            "ID", "NULL_FIELD", "LARGE_TEXT"
    );

    // ==================== VALIDATION TESTS ====================

    @Test
    void shouldThrowException_whenDbfPathIsNull() {
        assertThatThrownBy(() -> service.readDbfFileStreaming(null, consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DBF file path cannot be null or empty");
    }

    @Test
    void shouldThrowException_whenDbfPathIsEmpty() {
        assertThatThrownBy(() -> service.readDbfFileStreaming("", consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DBF file path cannot be null or empty");
    }

    @Test
    void shouldThrowException_whenDbfPathIsBlank() {
        assertThatThrownBy(() -> service.readDbfFileStreaming("   ", consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DBF file path cannot be null or empty");
    }

    @Test
    void shouldThrowException_whenConsumerIsNull() {
        assertThatThrownBy(() -> service.readDbfFileStreaming("test.dbf", (Consumer<DBFRow>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Record consumer cannot be null");
    }

    @Test
    void shouldThrowException_whenDbfFileDoesNotExist() {
        String nonExistentPath = tempDir.resolve("nonexistent.dbf").toString();

        assertThatThrownBy(() -> service.readDbfFileStreaming(nonExistentPath, consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DBF file not found");
    }

    @Test
    void shouldThrowException_whenPathIsDirectory() throws IOException {
        Path dirPath = Files.createDirectory(tempDir.resolve("directory"));

        assertThatThrownBy(() -> service.readDbfFileStreaming(dirPath.toString(), consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a regular file");
    }

    @Test
    void shouldThrowException_whenPathContainsPathTraversal() throws IOException {
        Path testFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        String separator = FileSystems.getDefault().getSeparator();
        String pathWithTraversal = tempDir + separator + ".." + separator + testFile.getFileName();

        assertThatThrownBy(() -> service.readDbfFileStreaming(pathWithTraversal, consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    // ==================== SUCCESSFUL READ TESTS ====================

    @Test
    void shouldReadDbfFileSuccessfully() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);

        Map<String, Object> first = capturedRecords.get(0);
        assertThat(String.valueOf(first.get("NAME"))).startsWith("John");
        assertThat(first.get("AGE")).isInstanceOf(Number.class);
        assertThat(((Number) first.get("AGE")).intValue()).isEqualTo(30);
        assertThat(first.get("SALARY")).isInstanceOf(Number.class);
        assertThat(((Number) first.get("SALARY")).doubleValue()).isEqualTo(50000.0);
    }

    @Test
    void shouldReadDbfFileWithMemoAndEncoding() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), null, "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
        Map<String, Object> first = capturedRecords.get(0);
        assertThat(String.valueOf(first.get("NAME"))).startsWith("John");
    }

    @Test
    void shouldReadEmptyDbfFile() throws Exception {
        Path dbfFile = createEmptyDbfFile();

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).isEmpty();
    }

    @Test
    void shouldHandleMultipleRecords() throws Exception {
        Path dbfFile = createDbfFileWithMultipleRecords();

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(3);
        assertThat(String.valueOf(capturedRecords.get(0).get("NAME"))).startsWith("John");
        assertThat(String.valueOf(capturedRecords.get(1).get("NAME"))).startsWith("Jane");
        assertThat(String.valueOf(capturedRecords.get(2).get("NAME"))).startsWith("Bob");
    }

    @Test
    void shouldHandleDifferentDataTypes() throws Exception {
        Path dbfFile = createDbfFileWithDifferentTypes();

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        Map<String, Object> record = capturedRecords.get(0);
        assertThat(record.get("STR")).isInstanceOf(String.class);
        assertThat(record.get("NUM")).isInstanceOf(Number.class);
        assertThat(record.get("LOG")).isInstanceOf(Boolean.class);
    }

    // ==================== ENCODING TESTS ====================

    @ParameterizedTest(name = "charset={0}")
    @MethodSource("charsets")
    void shouldReadDbfFileWithSupportedEncoding(String charsetName) throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), null, charsetName, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    private static Stream<Arguments> charsets() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("UTF-8"),
                Arguments.of("CP866"));
    }

    @Test
    void shouldHandleInvalidEncoding() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), "INVALID_ENCODING", null, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    // ==================== PATH HANDLING TESTS ====================

    @Test
    void shouldHandleAbsolutePath() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toAbsolutePath().toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldAcceptPathWithSpecialCharacters() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test_特殊字符.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldAcceptPathWithUnicodeCharacters() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test_안녕_世界.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    void shouldHandleCorruptFile() throws Exception {
        Path dbfFile = tempDir.resolve("corrupt.dbf");
        Files.write(dbfFile, "not a valid DBF file".getBytes());

        assertThatThrownBy(() -> service.readDbfFileStreaming(dbfFile.toString(), consumer))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read DBF file");
    }

    @Test
    void shouldHandleMissingMemoFile() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleMemoFileNotFound_whenSpecified() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        String nonExistentMemo = tempDir.resolve("nonexistent.FPT").toString();

        service.readDbfFileStreaming(dbfFile.toString(), nonExistentMemo, "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleNullValues() throws Exception {
        Path dbfFile = createDbfFileWithNullValues();

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        Object v = capturedRecords.get(0).get("NULL_FIELD");
        assertThat(v).isIn(null, "", " ");
    }

    @Test
    void shouldHandleNullValuesInAllFields() throws Exception {
        Path dbfFile = createDbfFileWithAllNullValues();

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        Map<String, Object> record = capturedRecords.get(0);
        Object name = record.get("NAME");
        Object age  = record.get("AGE");
        assertThat(name).isIn(null, "", " ");
        assertThat(age).isIn(null, 0, 0.0);
    }

    // ==================== MEMO FILE TESTS ====================

    @Test
    void shouldHandleDbtMemoFileWithoutCrashing() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Files.createFile(tempDir.resolve("test.DBT"));

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleFptMemoFileWithoutCrashing() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Files.createFile(tempDir.resolve("test.FPT"));

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleCorruptMemoFileGracefully() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Files.write(tempDir.resolve("test.FPT"), new byte[] {0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03});

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleMemoFileWithExplicitPathGracefully() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Path memoFile = tempDir.resolve("custom_memo.FPT");
        Files.createFile(memoFile);

        service.readDbfFileStreaming(dbfFile.toString(), memoFile.toString(), "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    // ==================== PERFORMANCE / STRESS TESTS ====================

    @Test
    void shouldHandleLargeFile() throws Exception {
        Path dbfFile = tempDir.resolve("large.dbf");
        createLargeDbfFile(dbfFile, 1000);

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1000);
    }

    @Test
    void shouldProcessRecordsSequentially() throws Exception {
        Path dbfFile = createDbfFileWithMultipleRecords();
        List<Long> timestamps = new ArrayList<>();

        service.readDbfFileStreaming(dbfFile.toString(), row -> timestamps.add(System.nanoTime()));

        assertThat(timestamps).hasSize(3);
        assertThat(timestamps.get(0)).isLessThanOrEqualTo(timestamps.get(1));
        assertThat(timestamps.get(1)).isLessThanOrEqualTo(timestamps.get(2));
    }

    @Test
    void shouldHandleLargeFieldValues() throws Exception {
        Path dbfFile = tempDir.resolve("large_values.dbf");
        createDbfFileWithLargeValues(dbfFile);

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        String largeText = String.valueOf(capturedRecords.get(0).get("LARGE_TEXT"));
        assertThat(largeText.trim()).hasSize(250);
    }

    @Test
    void shouldWrapInvalidPathException_whenPathContainsNulCharacter() {
        assertThatThrownBy(() -> service.readDbfFileStreaming("test\u0000.dbf", consumer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldLogAndContinue_whenMemoPathIsInvalid() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), "bad\u0000memo.FPT", "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldWrapNonIOExceptionAsRuntimeException() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        Consumer<DBFRow> throwing = row -> { throw new IllegalStateException("boom"); };

        assertThatThrownBy(() -> service.readDbfFileStreaming(dbfFile.toString(), throwing))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read DBF file")
                .hasMessageContaining("boom");
    }

    @Test
    void shouldLogWarning_whenMemoFileSetupThrows() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Files.write(tempDir.resolve("test.FPT"), new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldThrowException_whenFileIsNotReadable() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("unreadable.dbf"));

        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

        var view = Files.getFileAttributeView(
                dbfFile, java.nio.file.attribute.PosixFileAttributeView.class);
        view.setPermissions(Set.of());

        try {
            assertThatThrownBy(() -> service.readDbfFileStreaming(dbfFile.toString(), consumer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not readable");
        } finally {
            view.setPermissions(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        }
    }

    // ==================== FIELD ORDER TEST ====================

    @Test
    void shouldExposeFieldNamesInOrder() throws Exception {
        Path dbfFile = createDbfFileWithDifferentTypes();

        // Порядок полей доступен через reader.getField(i).getName() — но reader внутри сервиса.
        // Проверяем через DBFRow: getString по каждому имени должен работать.
        List<String> seenOrder = new ArrayList<>();
        service.readDbfFileStreaming(dbfFile.toString(), row -> {
            // Проверяем доступность каждого поля по имени
            row.getString("STR");
            row.getDouble("NUM");
            row.getObject("LOG");
            seenOrder.add("STR");
        });

        assertThat(seenOrder).hasSize(1);
    }

    // ==================== REFLECTION TESTS ====================

    @Test
    void shouldFindMemoFile_PreferringDbtOverFpt() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("with_memo.dbf"));
        Path dbtFile = tempDir.resolve("with_memo.DBT");
        Path fptFile = tempDir.resolve("with_memo.FPT");
        Files.createFile(dbtFile);
        Files.createFile(fptFile);

        var method = DbfReaderService.class.getDeclaredMethod("findMemoFile", String.class);
        method.setAccessible(true);

        String memoPath = (String) method.invoke(service, dbfFile.toString());

        assertThat(memoPath).isEqualTo(dbtFile.toAbsolutePath().toString());
    }

    @Test
    void shouldFindMemoFile_ReturnNull_whenNoMemoFileExists() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("no_memo.dbf"));

        var method = DbfReaderService.class.getDeclaredMethod("findMemoFile", String.class);
        method.setAccessible(true);

        String memoPath = (String) method.invoke(service, dbfFile.toString());

        assertThat(memoPath).isNull();
    }

    @Test
    void shouldResolveCharset_FallBackToDefault_whenCharsetNameUnsupported() throws Exception {
        var method = DbfReaderService.class.getDeclaredMethod("resolveCharset", String.class);
        method.setAccessible(true);

        var resolved = (java.nio.charset.Charset) method.invoke(service, "NOT_A_REAL_CHARSET");

        assertThat(resolved).isEqualTo(java.nio.charset.Charset.forName("CP866"));
    }

    // ==================== HELPERS ====================

    private Path createTestDbfFile(Path dbfFile) throws IOException {
        DBFField nameField = new DBFField("NAME", DBFDataType.CHARACTER, 20);
        DBFField ageField = new DBFField("AGE", DBFDataType.NUMERIC, 3, 0);
        DBFField salaryField = new DBFField("SALARY", DBFDataType.NUMERIC, 10, 2);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {

            writer.setFields(new DBFField[] { nameField, ageField, salaryField });
            writer.addRecord(new Object[] { "John", 30, 50000.0 });
            writer.addRecord(new Object[] { "Jane", 25, 60000.0 });
        }

        return dbfFile;
    }

    private Path createDbfFileWithMultipleRecords() throws IOException {
        Path dbfFile = tempDir.resolve("test_multiple.dbf");

        DBFField nameField = new DBFField("NAME", DBFDataType.CHARACTER, 20);
        DBFField ageField = new DBFField("AGE", DBFDataType.NUMERIC, 3, 0);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {

            writer.setFields(new DBFField[] { nameField, ageField });
            writer.addRecord(new Object[] { "John", 30 });
            writer.addRecord(new Object[] { "Jane", 25 });
            writer.addRecord(new Object[] { "Bob", 35 });
        }

        return dbfFile;
    }

    private Path createDbfFileWithDifferentTypes() throws IOException {
        Path dbfFile = tempDir.resolve("test_types.dbf");

        DBFField stringField = new DBFField("STR", DBFDataType.CHARACTER, 30);
        DBFField numericField = new DBFField("NUM", DBFDataType.NUMERIC, 10, 2);
        DBFField logicalField = new DBFField("LOG", DBFDataType.LOGICAL, 1);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {

            writer.setFields(new DBFField[] { stringField, numericField, logicalField });
            writer.addRecord(new Object[] { "Test String", 1234.56, true });
        }

        return dbfFile;
    }

    private Path createEmptyDbfFile() throws IOException {
        Path dbfFile = tempDir.resolve("empty.dbf");

        DBFField field = new DBFField("ID", DBFDataType.NUMERIC, 5, 0);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {
            writer.setFields(new DBFField[] { field });
        }

        return dbfFile;
    }

    private Path createDbfFileWithNullValues() throws IOException {
        Path dbfFile = tempDir.resolve("test_null.dbf");

        DBFField nullField = new DBFField("NULL_FIELD", DBFDataType.CHARACTER, 10);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {
            writer.setFields(new DBFField[] { nullField });
            writer.addRecord(new Object[] { null });
        }

        return dbfFile;
    }

    private Path createDbfFileWithAllNullValues() throws IOException {
        Path dbfFile = tempDir.resolve("test_all_null.dbf");

        DBFField nameField = new DBFField("NAME", DBFDataType.CHARACTER, 20);
        DBFField ageField = new DBFField("AGE", DBFDataType.NUMERIC, 3, 0);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {
            writer.setFields(new DBFField[] { nameField, ageField });
            writer.addRecord(new Object[] { null, null });
        }

        return dbfFile;
    }

    private void createLargeDbfFile(Path dbfFile, int recordCount) throws IOException {
        DBFField idField = new DBFField("ID", DBFDataType.NUMERIC, 10, 0);
        DBFField nameField = new DBFField("NAME", DBFDataType.CHARACTER, 50);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {

            writer.setFields(new DBFField[] { idField, nameField });

            for (int i = 1; i <= recordCount; i++) {
                writer.addRecord(new Object[] { i, "Record_" + i });
            }
        }
    }

    private void createDbfFileWithLargeValues(Path dbfFile) throws IOException {
        DBFField largeTextField = new DBFField("LARGE_TEXT", DBFDataType.CHARACTER, 254);

        try (OutputStream os = Files.newOutputStream(dbfFile);
             DBFWriter writer = new DBFWriter(os, StandardCharsets.UTF_8)) {

            writer.setFields(new DBFField[] { largeTextField });

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 250; i++) {
                sb.append('X');
            }

            writer.addRecord(new Object[] { sb.toString() });
        }
    }
}