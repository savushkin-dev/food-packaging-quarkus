package org.acme.foodpackaging.service.materials;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFField;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Сервис для потокового чтения DBF файлов
 */
@Slf4j
@ApplicationScoped
public class DbfReaderService {

    private static final String DEFAULT_ENCODING = "CP866";

    /**
     * Потоковое чтение DBF файла с memo и кодировкой
     */
    public void readDbfFileStreaming(String dbfPath, String memoPath, String charsetName,
                                     Consumer<Map<String, Object>> recordConsumer) {
        validateInput(dbfPath, recordConsumer);

        // Валидируем путь
        String validatedPath = validatePath(dbfPath);
        Path dbfFilePath = Paths.get(validatedPath);

        if (!Files.exists(dbfFilePath) || !Files.isRegularFile(dbfFilePath)) {
            throw new IllegalArgumentException("DBF file not found: " + dbfPath);
        }

        log.info("Opening DBF file: {}", sanitizeForLog(dbfFilePath.getFileName().toString()));

        try (DBFReader reader = new DBFReader(new FileInputStream(dbfFilePath.toFile()))) {
            setupReader(reader, charsetName, memoPath);

            DBFField[] fields = readStructure(reader, dbfFilePath);
            processRecords(reader, fields, recordConsumer);

        } catch (IOException e) {
            log.error("IO error reading DBF file: {}", sanitizeForLog(dbfPath), e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error reading DBF file: {}", sanitizeForLog(dbfPath), e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        }
    }

    /**
     * Упрощенный метод - автоматически ищет memo файл
     */
    public void readDbfFileStreaming(String dbfPath, Consumer<Map<String, Object>> recordConsumer) {
        validateInput(dbfPath, recordConsumer);
        String validatedPath = validatePath(dbfPath);
        String memoPath = findMemoFile(validatedPath);
        readDbfFileStreaming(validatedPath, memoPath, DEFAULT_ENCODING, recordConsumer);
    }

    // ==================== PRIVATE METHODS ====================

    private void validateInput(String dbfPath, Consumer<Map<String, Object>> recordConsumer) {
        if (dbfPath == null || dbfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("DBF file path cannot be null or empty");
        }
        if (recordConsumer == null) {
            throw new IllegalArgumentException("Record consumer cannot be null");
        }
    }

    /**
     * Валидация пути для предотвращения path injection
     * Пропускает безопасные символы: буквы, цифры, пробелы, точки, дефисы, underscores, слеши
     */
    private String validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Проверяем на опасные символы - блокируем только явно опасные
        // Разрешаем: a-z, A-Z, 0-9, пробелы, точки, дефисы, underscores, слеши, обратные слеши, двоеточие
        if (path.matches(".*[<>|\"?*].*")) {
            throw new IllegalArgumentException("Invalid path contains unsafe characters");
        }

        // Проверяем на попытку обхода директории
        Path normalizedPath = Paths.get(path).normalize();
        if (normalizedPath.toString().contains("..")) {
            throw new IllegalArgumentException("Invalid path: directory traversal detected");
        }

        return normalizedPath.toString();
    }

    /**
     * Санитизация данных для логирования
     */
    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        return input.replaceAll("[\n\r\t]", "_");
    }

    private String findMemoFile(String dbfPath) {
        String basePath = dbfPath.substring(0, dbfPath.lastIndexOf('.'));
        String dbtPath = basePath + ".DBT";
        String fptPath = basePath + ".FPT";

        String validatedDbtPath = validatePath(dbtPath);
        String validatedFptPath = validatePath(fptPath);

        if (Files.exists(Paths.get(validatedDbtPath)) && Files.isRegularFile(Paths.get(validatedDbtPath))) {
            log.info("Found DBT memo file: {}", sanitizeForLog(validatedDbtPath));
            return validatedDbtPath;
        } else if (Files.exists(Paths.get(validatedFptPath)) && Files.isRegularFile(Paths.get(validatedFptPath))) {
            log.info("Found FPT memo file: {}", sanitizeForLog(validatedFptPath));
            return validatedFptPath;
        }
        log.info("No memo file found for: {}", sanitizeForLog(dbfPath));
        return null;
    }

    private void setupReader(DBFReader reader, String charsetName, String memoPath) throws IOException {
        String encoding = charsetName != null ? charsetName : DEFAULT_ENCODING;
        reader.setCharactersetName(encoding);

        if (memoPath != null && !memoPath.trim().isEmpty()) {
            String validatedMemoPath = validatePath(memoPath);
            Path memoFilePath = Paths.get(validatedMemoPath);
            if (Files.exists(memoFilePath) && Files.isRegularFile(memoFilePath)) {
                reader.setMemoFile(memoFilePath.toFile());
                log.info("Memo file loaded: {}", sanitizeForLog(validatedMemoPath));
            } else {
                log.warn("Memo file not found: {}", sanitizeForLog(validatedMemoPath));
            }
        }
    }

    private DBFField[] readStructure(DBFReader reader, Path dbfFilePath) {
        int fieldCount = reader.getFieldCount();
        DBFField[] fields = new DBFField[fieldCount];

        log.info("=== DBF Structure: {} ===", sanitizeForLog(dbfFilePath.getFileName().toString()));
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = reader.getField(i);
            log.info("Field {}: {} ({}) length: {}",
                    i,
                    sanitizeForLog(fields[i].getName()),
                    fields[i].getType().name(),
                    fields[i].getLength());
        }
        return fields;
    }

    private void processRecords(DBFReader reader, DBFField[] fields,
                                Consumer<Map<String, Object>> recordConsumer) throws IOException {
        Object[] rowData;
        int rowCount = 0;

        while ((rowData = reader.nextRecord()) != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < fields.length; i++) {
                row.put(fields[i].getName(), rowData[i] != null ? rowData[i] : null);
            }
            recordConsumer.accept(row);
            rowCount++;
        }

        log.info("Total records processed: {}", rowCount);
    }
}