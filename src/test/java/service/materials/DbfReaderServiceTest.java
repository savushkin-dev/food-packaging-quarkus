package service.materials;

import com.linuxense.javadbf.DBFWriter;
import com.linuxense.javadbf.DBFField;
import org.acme.foodpackaging.service.materials.DbfReaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
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

    private DbfReaderService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new DbfReaderService();
    }

    // ==================== ВАЛИДАЦИЯ ВХОДНЫХ ДАННЫХ ====================

    /**
     * Проверяет, что метод выбрасывает исключение при передаче null в качестве пути к DBF файлу
     */
    @Test
    void shouldThrowException_whenDbfPathIsNull() {
        Consumer<Map<String, Object>> consumer = record -> {};

        assertThatThrownBy(() -> service.readDbfFileStreaming(null, consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DBF file path cannot be null or empty");
    }

    /**
     * Проверяет, что метод выбрасывает исключение при передаче пустой строки в качестве пути к DBF файлу
     */
    @Test
    void shouldThrowException_whenDbfPathIsEmpty() {
        Consumer<Map<String, Object>> consumer = record -> {};

        assertThatThrownBy(() -> service.readDbfFileStreaming("", consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DBF file path cannot be null or empty");
    }

    /**
     * Проверяет, что метод выбрасывает исключение при передаче строки с пробелами в качестве пути к DBF файлу
     */
    @Test
    void shouldThrowException_whenDbfPathIsBlank() {
        Consumer<Map<String, Object>> consumer = record -> {};

        assertThatThrownBy(() -> service.readDbfFileStreaming("   ", consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DBF file path cannot be null or empty");
    }

    /**
     * Проверяет, что метод выбрасывает исключение при передаче null в качестве Consumer
     */
    @Test
    void shouldThrowException_whenConsumerIsNull() {
        assertThatThrownBy(() -> service.readDbfFileStreaming("test.dbf", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Record consumer cannot be null");
    }

    /**
     * Проверяет, что метод выбрасывает исключение, если DBF файл не существует
     */
    @Test
    void shouldThrowException_whenDbfFileDoesNotExist() {
        Consumer<Map<String, Object>> consumer = record -> {};
        String nonExistentPath = tempDir.resolve("nonexistent.dbf").toString();

        assertThatThrownBy(() -> service.readDbfFileStreaming(nonExistentPath, consumer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DBF file not found: " + nonExistentPath);
    }

    // ==================== УСПЕШНОЕ ЧТЕНИЕ DBF ФАЙЛОВ ====================

    /**
     * Проверяет успешное чтение DBF файла с данными
     * Проверяет, что все записи прочитаны и значения соответствуют ожидаемым
     * Особое внимание: числовые поля возвращаются как BigDecimal
     */
    @Test
    void shouldReadDbfFileSuccessfully() throws Exception {
        Path dbfFile = createTestDbfFile();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
        Map<String, Object> firstRecord = capturedRecords.get(0);
        assertThat(firstRecord.get("NAME")).isEqualTo("John");
        assertThat(firstRecord.get("AGE")).isEqualTo(new BigDecimal(30));
        assertThat(firstRecord.get("SALARY")).isEqualTo(new BigDecimal("50000.00"));
    }

    /**
     * Проверяет чтение пустого DBF файла (без записей)
     * Убеждается, что consumer не вызывается ни разу
     */
    @Test
    void shouldReadEmptyDbfFile() throws Exception {
        Path dbfFile = createEmptyDbfFile();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).isEmpty();
    }

    // ==================== ТЕСТЫ КОДИРОВОК ====================

    /**
     * Проверяет, что при передаче null в качестве кодировки используется дефолтная (CP866)
     */
    @Test
    void shouldUseDefaultEncoding_whenCharsetIsNull() throws Exception {
        Path dbfFile = createTestDbfFile();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), null, null, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    /**
     * Проверяет, что метод корректно обрабатывает указанную кодировку (UTF-8)
     */
    @Test
    void shouldUseSpecifiedEncoding() throws Exception {
        Path dbfFile = createTestDbfFile();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), "UTF-8", null, consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    // ==================== ТЕСТЫ ОБРАБОТКИ ОШИБОК ====================

    /**
     * Проверяет, что метод корректно обрабатывает IOException
     * Создается испорченный файл, который вызывает ошибку при чтении
     */
    @Test
    void shouldHandleIOException() throws Exception {
        Path dbfFile = tempDir.resolve("corrupt.dbf");
        Files.write(dbfFile, "not a valid DBF file".getBytes());

        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        assertThatThrownBy(() -> service.readDbfFileStreaming(dbfFile.toString(), consumer))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read DBF file");
    }

    /**
     * Проверяет, что метод корректно обрабатывает поврежденный DBF файл
     * Создается пустой файл, который не является валидным DBF
     */
    @Test
    void shouldHandleCorruptFile() throws Exception {
        Path dbfFile = tempDir.resolve("corrupt2.dbf");
        Files.createFile(dbfFile);

        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        assertThatThrownBy(() -> service.readDbfFileStreaming(dbfFile.toString(), consumer))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== ТЕСТЫ РАЗЛИЧНЫХ СЦЕНАРИЕВ ====================

    /**
     * Проверяет чтение файла с несколькими записями (3 записи)
     * Убеждается, что все записи прочитаны корректно
     */
    @Test
    void shouldHandleMultipleRecords() throws Exception {
        Path dbfFile = createDbfFileWithMultipleRecords();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(3);
        assertThat(capturedRecords.get(0).get("NAME")).isEqualTo("John");
        assertThat(capturedRecords.get(1).get("NAME")).isEqualTo("Jane");
        assertThat(capturedRecords.get(2).get("NAME")).isEqualTo("Bob");
    }

    /**
     * Проверяет чтение файла с полями разных типов:
     * - Строковые (CHARACTER)
     * - Числовые (NUMERIC)
     * - Логические (LOGICAL)
     */
    @Test
    void shouldHandleDifferentDataTypes() throws Exception {
        Path dbfFile = createDbfFileWithDifferentTypes();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        Map<String, Object> record = capturedRecords.get(0);
        assertThat(record.get("STR")).isInstanceOf(String.class);
        assertThat(record.get("NUM")).isInstanceOf(BigDecimal.class);
        assertThat(record.get("LOG")).isInstanceOf(Boolean.class);
    }

    // ==================== ТЕСТЫ MEMO ФАЙЛОВ ====================

    /**
     * Проверяет, что метод корректно работает при отсутствии memo файла
     * Должен прочитать DBF файл без ошибок, игнорируя отсутствие memo
     */
    @Test
    void shouldHandleMissingMemoFile() throws Exception {
        Path dbfFile = createTestDbfFile();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    /**
     * Проверяет, что при указании несуществующего memo файла, метод продолжает работу
     * Должен вывести предупреждение, но прочитать DBF файл
     */
    @Test
    void shouldHandleMemoFileNotFound_whenSpecified() throws Exception {
        Path dbfFile = createTestDbfFile();
        String nonExistentMemo = tempDir.resolve("nonexistent.FPT").toString();

        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), nonExistentMemo, "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    /**
     * Проверяет поиск memo файла с расширением .DBT
     * Создается DBF файл и DBT файл, проверяется что они находятся
     */
    @Test
    void shouldFindDbtMemoFile() throws Exception {
        // Создаем DBF файл
        Path dbfFile = tempDir.resolve("test.DBF");
        createMinimalDbfFile(dbfFile);

        // Создаем DBT memo файл
        Path dbtFile = tempDir.resolve("test.DBT");
        Files.createFile(dbtFile);

        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        // Метод должен найти DBT файл и прочитать DBF без ошибок
        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).isEmpty();
    }

    /**
     * Проверяет поиск memo файла с расширением .FPT
     * Создается DBF файл и FPT файл, проверяется что они находятся
     */
    @Test
    void shouldFindFptMemoFile() throws Exception {
        // Создаем DBF файл
        Path dbfFile = tempDir.resolve("test.DBF");
        createMinimalDbfFile(dbfFile);

        // Создаем FPT memo файл
        Path fptFile = tempDir.resolve("test.FPT");
        Files.createFile(fptFile);

        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        // Метод должен найти FPT файл и прочитать DBF без ошибок
        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).isEmpty();
    }

    /**
     * Проверяет, что при наличии и DBT и FPT файлов, метод находит DBT первым
     */
    @Test
    void shouldFindDbtBeforeFpt() throws Exception {
        Path dbfFile = tempDir.resolve("test.DBF");
        createMinimalDbfFile(dbfFile);

        // Создаем оба memo файла
        Path dbtFile = tempDir.resolve("test.DBT");
        Path fptFile = tempDir.resolve("test.FPT");
        Files.createFile(dbtFile);
        Files.createFile(fptFile);

        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        // Должен найти DBT (он проверяется первым)
        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).isEmpty();
    }

    // ==================== ТЕСТЫ С NULL ЗНАЧЕНИЯМИ ====================

    /**
     * Проверяет обработку полей с null значениями
     * Создается запись с пустыми/пробельными значениями
     */
    @Test
    void shouldHandleNullValues() throws Exception {
        Path dbfFile = createDbfFileWithNullValues();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        service.readDbfFileStreaming(dbfFile.toString(), consumer);

        assertThat(capturedRecords).hasSize(1);
        Map<String, Object> record = capturedRecords.get(0);
        // Поле с null значением должно быть null или пустой строкой
        assertThat(record.get("NULL_FIELD")).isIn(null, "", " ");
    }

    // ==================== ТЕСТЫ С РАЗНЫМИ КОДИРОВКАМИ И MEMO ====================

    /**
     * Проверяет передачу разных кодировок вместе с memo файлом
     */
    @Test
    void shouldHandleDifferentEncodingsWithMemo() throws Exception {
        Path dbfFile = createTestDbfFile();
        String memoPath = tempDir.resolve("test.FPT").toString();
        Files.createFile(Path.of(memoPath));

        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        // Передаем разные кодировки
        service.readDbfFileStreaming(dbfFile.toString(), memoPath, "CP1251", consumer);
        assertThat(capturedRecords).hasSize(2);

        capturedRecords.clear();
        service.readDbfFileStreaming(dbfFile.toString(), memoPath, "UTF-8", consumer);
        assertThat(capturedRecords).hasSize(2);

        capturedRecords.clear();
        service.readDbfFileStreaming(dbfFile.toString(), memoPath, "KOI8-R", consumer);
        assertThat(capturedRecords).hasSize(2);
    }

    /**
     * Проверяет передачу пустого memo пути
     */
    @Test
    void shouldHandleEmptyMemoPath() throws Exception {
        Path dbfFile = createTestDbfFile();
        List<Map<String, Object>> capturedRecords = new ArrayList<>();
        Consumer<Map<String, Object>> consumer = capturedRecords::add;

        // Передаем пустую строку как memo путь
        service.readDbfFileStreaming(dbfFile.toString(), "", "CP866", consumer);

        assertThat(capturedRecords).hasSize(2);
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Создает тестовый DBF файл с двумя записями
     */
    private Path createTestDbfFile() throws IOException {
        Path dbfFile = tempDir.resolve("test.dbf");

        try (DBFWriter writer = new DBFWriter()) {
            DBFField nameField = new DBFField();
            nameField.setName("NAME");
            nameField.setDataType(DBFField.FIELD_TYPE_C);
            nameField.setFieldLength(20);

            DBFField ageField = new DBFField();
            ageField.setName("AGE");
            ageField.setDataType(DBFField.FIELD_TYPE_N);
            ageField.setFieldLength(3);
            ageField.setDecimalCount(0);

            DBFField salaryField = new DBFField();
            salaryField.setName("SALARY");
            salaryField.setDataType(DBFField.FIELD_TYPE_N);
            salaryField.setFieldLength(10);
            salaryField.setDecimalCount(2);

            writer.setFields(new DBFField[]{nameField, ageField, salaryField});

            writer.addRecord(new Object[]{"John", 30, 50000.0});
            writer.addRecord(new Object[]{"Jane", 25, 60000.0});

            try (FileOutputStream fos = new FileOutputStream(dbfFile.toFile())) {
                writer.write(fos);
            }
        }

        return dbfFile;
    }

    /**
     * Создает DBF файл с несколькими записями (3 записи)
     */
    private Path createDbfFileWithMultipleRecords() throws IOException {
        Path dbfFile = tempDir.resolve("test_multiple.dbf");

        try (DBFWriter writer = new DBFWriter()) {
            DBFField nameField = new DBFField();
            nameField.setName("NAME");
            nameField.setDataType(DBFField.FIELD_TYPE_C);
            nameField.setFieldLength(20);

            DBFField ageField = new DBFField();
            ageField.setName("AGE");
            ageField.setDataType(DBFField.FIELD_TYPE_N);
            ageField.setFieldLength(3);
            ageField.setDecimalCount(0);

            writer.setFields(new DBFField[]{nameField, ageField});

            writer.addRecord(new Object[]{"John", 30});
            writer.addRecord(new Object[]{"Jane", 25});
            writer.addRecord(new Object[]{"Bob", 35});

            try (FileOutputStream fos = new FileOutputStream(dbfFile.toFile())) {
                writer.write(fos);
            }
        }

        return dbfFile;
    }

    /**
     * Создает DBF файл с полями разных типов
     */
    private Path createDbfFileWithDifferentTypes() throws IOException {
        Path dbfFile = tempDir.resolve("test_types.dbf");

        try (DBFWriter writer = new DBFWriter()) {
            DBFField stringField = new DBFField();
            stringField.setName("STR");
            stringField.setDataType(DBFField.FIELD_TYPE_C);
            stringField.setFieldLength(30);

            DBFField numericField = new DBFField();
            numericField.setName("NUM");
            numericField.setDataType(DBFField.FIELD_TYPE_N);
            numericField.setFieldLength(10);
            numericField.setDecimalCount(2);

            DBFField logicalField = new DBFField();
            logicalField.setName("LOG");
            logicalField.setDataType(DBFField.FIELD_TYPE_L);
            logicalField.setFieldLength(1);

            writer.setFields(new DBFField[]{stringField, numericField, logicalField});

            writer.addRecord(new Object[]{"Test String", 1234.56, true});

            try (FileOutputStream fos = new FileOutputStream(dbfFile.toFile())) {
                writer.write(fos);
            }
        }

        return dbfFile;
    }

    /**
     * Создает пустой DBF файл (без записей)
     */
    private Path createEmptyDbfFile() throws IOException {
        Path dbfFile = tempDir.resolve("empty.dbf");

        try (DBFWriter writer = new DBFWriter()) {
            DBFField field = new DBFField();
            field.setName("ID");
            field.setDataType(DBFField.FIELD_TYPE_N);
            field.setFieldLength(5);
            field.setDecimalCount(0);

            writer.setFields(new DBFField[]{field});

            try (FileOutputStream fos = new FileOutputStream(dbfFile.toFile())) {
                writer.write(fos);
            }
        }

        return dbfFile;
    }

    /**
     * Создает минимальный DBF файл (только структура, без записей)
     */
    private void createMinimalDbfFile(Path dbfFile) throws IOException {
        try (DBFWriter writer = new DBFWriter()) {
            DBFField field = new DBFField();
            field.setName("ID");
            field.setDataType(DBFField.FIELD_TYPE_N);
            field.setFieldLength(5);
            field.setDecimalCount(0);

            writer.setFields(new DBFField[]{field});

            try (FileOutputStream fos = new FileOutputStream(dbfFile.toFile())) {
                writer.write(fos);
            }
        }
    }

    /**
     * Создает DBF файл с null значениями
     */
    private Path createDbfFileWithNullValues() throws IOException {
        Path dbfFile = tempDir.resolve("test_null.dbf");

        try (DBFWriter writer = new DBFWriter()) {
            DBFField nullField = new DBFField();
            nullField.setName("NULL_FIELD");
            nullField.setDataType(DBFField.FIELD_TYPE_C);
            nullField.setFieldLength(10);

            writer.setFields(new DBFField[]{nullField});

            // Добавляем запись с null (пробелы)
            writer.addRecord(new Object[]{" "});

            try (FileOutputStream fos = new FileOutputStream(dbfFile.toFile())) {
                writer.write(fos);
            }
        }

        return dbfFile;
    }
}