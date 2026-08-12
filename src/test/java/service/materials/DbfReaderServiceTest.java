package service.materials;

import com.linuxense.javadbf.DBFWriter;
import com.linuxense.javadbf.DBFField;
import org.acme.foodpackaging.service.materials.DbfReaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
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
                .hasMessage("DBF file not found: " + nonExistentPath);
    }

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
        assertThat(record.get("NULL_FIELD")).isIn("", " ", null);
    }

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

    // ==================== HELPER METHODS ====================

    private Path createTestDbfFile(Path dbfFile) throws IOException {
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

    private Path createDbfFileWithNullValues() throws IOException {
        Path dbfFile = tempDir.resolve("test_null.dbf");

        try (DBFWriter writer = new DBFWriter()) {
            DBFField nullField = new DBFField();
            nullField.setName("NULL_FIELD");
            nullField.setDataType(DBFField.FIELD_TYPE_C);
            nullField.setFieldLength(10);

            writer.setFields(new DBFField[]{nullField});

            writer.addRecord(new Object[]{" "});

            try (FileOutputStream fos = new FileOutputStream(dbfFile.toFile())) {
                writer.write(fos);
            }
        }

        return dbfFile;
    }
}