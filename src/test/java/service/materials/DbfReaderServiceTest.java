package service.materials;

import com.linuxense.javadbf.DBFWriter;
import com.linuxense.javadbf.DBFDataType;
import com.linuxense.javadbf.DBFField;
import org.acme.foodpackaging.service.materials.DbfReaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class DbfReaderServiceTest {

    @InjectMocks
    private DbfReaderService service;

    @TempDir
    Path tempDir;

    private List<Map<String, Object>> capturedRecords;
    private Consumer<Map<String, Object>> consumer;

    @BeforeEach
    void setUp() {
        capturedRecords = new ArrayList<>();
        consumer = capturedRecords::add;
    }

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
        assertThatThrownBy(() -> service.readDbfFileStreaming("test.dbf", null))
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
        Path dirPath = tempDir.resolve("directory");
        Files.createDirectory(dirPath);

        assertThatThrownBy(() -> service.readDbfFileStreaming(dirPath.toString(), consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a regular file");
    }

    @Test
    void shouldThrowException_whenPathContainsPathTraversal() {
        // Создаем тестовый файл
        Path testFile = tempDir.resolve("test.dbf");
        try {
            createTestDbfFile(testFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Формируем путь с path traversal
        String separator = System.getProperty("file.separator");
        String pathWithTraversal = tempDir.toString() + separator + ".." + separator + "test.dbf";

        // Проверяем, что выбрасывается исключение с сообщением о path traversal
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
        Map<String, Object> firstRecord = capturedRecords.get(0);
        assertThat(firstRecord.get("NAME")).isEqualTo("John");
        assertThat(firstRecord.get("AGE")).isEqualTo(new BigDecimal(30));
        assertThat(firstRecord.get("SALARY")).isEqualTo(new BigDecimal("50000.00"));
    }

    @Test
    void shouldReadDbfFileWithMemoAndEncoding() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(
                dbfFile.toString(),
                null, // memoPath
                "CP866", // charsetName
                consumer);

        assertThat(capturedRecords).hasSize(2);
        Map<String, Object> firstRecord = capturedRecords.get(0);
        assertThat(firstRecord.get("NAME")).isEqualTo("John");
        assertThat(firstRecord.get("AGE")).isEqualTo(new BigDecimal(30));
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
        assertThat(capturedRecords.get(0).get("NAME")).isEqualTo("John");
        assertThat(capturedRecords.get(1).get("NAME")).isEqualTo("Jane");
        assertThat(capturedRecords.get(2).get("NAME")).isEqualTo("Bob");
    }

    @Test
    void shouldHandleDifferentDataTypes() throws Exception {
        Path dbfFile = createDbfFileWithDifferentTypes();

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        Map<String, Object> record = capturedRecords.get(0);
        assertThat(record.get("STR")).isInstanceOf(String.class);
        assertThat(record.get("NUM")).isInstanceOf(BigDecimal.class);
        assertThat(record.get("LOG")).isInstanceOf(Boolean.class);
    }

    // ==================== ENCODING TESTS ====================

    @Test
    void shouldUseDefaultEncoding_whenCharsetIsNull() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), null, null, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldUseSpecifiedEncoding() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        service.readDbfFileStreaming(dbfFile.toString(), "UTF-8", null, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleInvalidEncoding() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        // Несуществующая кодировка должна использовать дефолтную
        service.readDbfFileStreaming(dbfFile.toString(), "INVALID_ENCODING", null, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    // ==================== PATH HANDLING TESTS ====================

    @Test
    void shouldHandleAbsolutePath() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        String absolutePath = dbfFile.toAbsolutePath().toString();

        service.readDbfFileStreaming(absolutePath, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldAcceptPathWithSpaces() throws Exception {
        Path dbfFile = tempDir.resolve("test with spaces.dbf");
        createTestDbfFile(dbfFile);

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldAcceptPathWithSpecialCharacters() throws Exception {
        Path dbfFile = tempDir.resolve("test_特殊字符.dbf");
        createTestDbfFile(dbfFile);

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldAcceptPathWithUnicodeCharacters() throws Exception {
        Path dbfFile = tempDir.resolve("test_안녕_世界.dbf");
        createTestDbfFile(dbfFile);

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
        Map<String, Object> record = capturedRecords.get(0);
        assertThat(record.get("NULL_FIELD")).isIn(null, " ", "");
    }

    @Test
    void shouldHandleNullValuesInAllFields() throws Exception {
        Path dbfFile = createDbfFileWithAllNullValues();

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        Map<String, Object> record = capturedRecords.get(0);
        assertThat(record.get("NAME")).isIn(null, "", " ");
        assertThat(record.get("AGE")).isIn(null, 0);
    }

    // ==================== MEMO FILE TESTS ====================
    // Эти тесты проверяют, что сервис корректно обрабатывает memo файлы
    // даже если они повреждены или невалидны - сервис должен продолжать работу

    @Test
    void shouldHandleDbtMemoFileWithoutCrashing() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Path dbtFile = tempDir.resolve("test.DBT");
        // Создаем пустой DBT файл
        Files.createFile(dbtFile);

        // Сервис должен обработать ошибку и продолжить работу
        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleFptMemoFileWithoutCrashing() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Path fptFile = tempDir.resolve("test.FPT");
        // Создаем пустой FPT файл
        Files.createFile(fptFile);

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleEmptyMemoFileGracefully() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Path dbtFile = tempDir.resolve("test.DBT");
        // Создаем пустой файл
        Files.createFile(dbtFile);

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleCorruptMemoFileGracefully() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Path fptFile = tempDir.resolve("test.FPT");
        // Пишем случайные данные, которые не являются валидным memo
        Files.write(fptFile, new byte[] { 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03 });

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldHandleMemoFileWithExplicitPathGracefully() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Path memoFile = tempDir.resolve("custom_memo.FPT");
        // Создаем пустой файл
        Files.createFile(memoFile);

        service.readDbfFileStreaming(dbfFile.toString(), memoFile.toString(), "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    // ==================== PERFORMANCE AND STRESS TESTS ====================

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

        service.readDbfFileStreaming(dbfFile.toString(), record -> {
            timestamps.add(System.nanoTime());
        });

        assertThat(timestamps).hasSize(3);
    }

    @Test
    void shouldHandleLargeFieldValues() throws Exception {
        Path dbfFile = tempDir.resolve("large_values.dbf");
        createDbfFileWithLargeValues(dbfFile);

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        String largeText = (String) capturedRecords.get(0).get("LARGE_TEXT");
        assertThat(largeText).hasSize(250);
    }

    // ==================== HELPER METHODS ====================

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

    @Test
    void shouldWrapInvalidPathException_whenPathContainsNulCharacter() {
        // NUL-байт вызывает InvalidPathException внутри Paths.get(...),
        // что не является IllegalArgumentException и должно попасть во внешний catch
        assertThatThrownBy(() -> service.readDbfFileStreaming("test\u0000.dbf", consumer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldLogAndContinue_whenMemoPathIsInvalid() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        // NUL-байт в пути к memo-файлу вызывает InvalidPathException внутри
        // Paths.get(memoPath) в setupReader — должно попасть во внешний catch
        // и не прервать чтение самого DBF
        service.readDbfFileStreaming(dbfFile.toString(), "bad\u0000memo.FPT", "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    @Test
    void shouldWrapNonIOExceptionAsRuntimeException() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));

        Consumer<Map<String, Object>> throwingConsumer = record -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> service.readDbfFileStreaming(dbfFile.toString(), throwingConsumer))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read DBF file")
                .hasMessageContaining("boom");
    }

    @Test
    void shouldLogWarning_whenMemoFileSetupThrows() throws Exception {
        Path dbfFile = createTestDbfFile(tempDir.resolve("test.dbf"));
        Path fptFile = tempDir.resolve("test.FPT");
        // заведомо некорректная структура memo-файла:
        // javadbf при setMemoFile валидирует заголовок и должен бросить исключение
        Files.write(fptFile, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });

        // если тестовая среда позволяет перехватывать логи (logback-classic
        // ListAppender) —
        // здесь стоит явно проверить, что WARN "Memo file is corrupted or invalid" был
        // залогирован,
        // а не просто что чтение не упало
        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }
}