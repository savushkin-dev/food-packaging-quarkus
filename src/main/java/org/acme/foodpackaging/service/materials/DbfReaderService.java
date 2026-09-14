package org.acme.foodpackaging.service.materials;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFField;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
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

        Path dbfFilePath = normalizeAndValidatePath(dbfPath);
        Charset encoding = resolveCharset(charsetName);

        log.info("Opening DBF file: {}", dbfFilePath.getFileName());

        // Кодировка теперь передаётся сразу в конструктор DBFReader,
        // а не выставляется постфактум через deprecated-сеттер.
        try (DBFReader reader = new DBFReader(new FileInputStream(dbfFilePath.toFile()), encoding, false)) {
            loadMemoFileIfPresent(reader, memoPath);

            DBFField[] fields = readStructure(reader, dbfFilePath);
            processRecords(reader, fields, recordConsumer);

        } catch (IOException e) {
            log.error("IO error reading DBF file: {}", dbfFilePath.getFileName(), e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error reading DBF file: {}", dbfFilePath.getFileName(), e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        }
    }

    /**
     * Упрощенный метод - автоматически ищет memo файл
     */
    public void readDbfFileStreaming(String dbfPath, Consumer<Map<String, Object>> recordConsumer) {
        validateInput(dbfPath, recordConsumer);
        String memoPath = findMemoFile(dbfPath);
        readDbfFileStreaming(dbfPath, memoPath, DEFAULT_ENCODING, recordConsumer);
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

    private Path normalizeAndValidatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("DBF" + " file path cannot be null or empty");
        }

        try {
            // Проверяем на path traversal ДО нормализации
            String normalizedPathStr = path.replace('\\', '/');
            if (normalizedPathStr.contains("..")) {
                throw new IllegalArgumentException("Invalid file path: path traversal detected");
            }

            Path normalizedPath = Paths.get(path).normalize().toAbsolutePath();

            // Проверяем, что файл существует и доступен
            if (!Files.exists(normalizedPath)) {
                throw new IllegalArgumentException("DBF" + " file not found: " + normalizedPath);
            }

            if (!Files.isRegularFile(normalizedPath)) {
                throw new IllegalArgumentException("DBF" + " path is not a regular file: " + normalizedPath);
            }


            if (!Files.isReadable(normalizedPath)) {
                throw new IllegalArgumentException("DBF" + " file is not readable: " + normalizedPath);
            }

            return normalizedPath;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + "DBF" + " file path: " + path, e);
        }
    }

    private String findMemoFile(String dbfPath) {
        try {
            Path dbfPathObj = Paths.get(dbfPath).normalize();
            String basePath = dbfPathObj.toString();
            int lastDot = basePath.lastIndexOf('.');
            if (lastDot > 0) {
                basePath = basePath.substring(0, lastDot);
            }

            String dbtPath = basePath + ".DBT";
            String fptPath = basePath + ".FPT";

            Path dbtPathObj = Paths.get(dbtPath).normalize().toAbsolutePath();
            if (Files.exists(dbtPathObj) && Files.isRegularFile(dbtPathObj) && Files.isReadable(dbtPathObj)) {
                log.info("Found DBT memo file: {}", dbtPathObj.getFileName());
                return dbtPathObj.toString();
            }

            Path fptPathObj = Paths.get(fptPath).normalize().toAbsolutePath();
            if (Files.exists(fptPathObj) && Files.isRegularFile(fptPathObj) && Files.isReadable(fptPathObj)) {
                log.info("Found FPT memo file: {}", fptPathObj.getFileName());
                return fptPathObj.toString();
            }

            log.info("No memo file found for: {}", dbfPathObj.getFileName());
            return null;
        } catch (Exception e) {
            log.warn("Error finding memo file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Превращает строковое имя кодировки в объект Charset.
     * Если имя некорректно или не поддерживается - откатываемся на кодировку по умолчанию.
     */
    private Charset resolveCharset(String charsetName) {
        String name = charsetName != null ? charsetName : DEFAULT_ENCODING;
        try {
            return Charset.forName(name);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            log.warn("Unsupported or invalid charset '{}', falling back to default '{}'", name, DEFAULT_ENCODING);
            return Charset.forName(DEFAULT_ENCODING);
        }
    }

    private void loadMemoFileIfPresent(DBFReader reader, String memoPath) {
        if (memoPath != null && !memoPath.trim().isEmpty()) {
            loadMemoFile(reader, memoPath);
        }
    }

    private void loadMemoFile(DBFReader reader, String memoPath) {
        try {
            // Проверяем, что memo файл существует и доступен для чтения
            Path memoFilePath = Paths.get(memoPath).normalize().toAbsolutePath();
            if (Files.exists(memoFilePath) && Files.isRegularFile(memoFilePath) && Files.isReadable(memoFilePath)) {
                setMemoFileSafely(reader, memoFilePath);
            } else {
                log.warn("Memo file not found or not accessible: {}", memoPath);
            }
        } catch (Exception e) {
            log.warn("Failed to load memo file: {}", memoPath, e);
        }
    }

    private void setMemoFileSafely(DBFReader reader, Path memoFilePath) {
        // Пытаемся установить memo файл, но если он поврежден - игнорируем
        try {
            reader.setMemoFile(memoFilePath.toFile());
            log.info("Memo file loaded: {}", memoFilePath.getFileName());
        } catch (Exception e) {
            log.warn("Memo file is corrupted or invalid: {}", memoFilePath.getFileName());
            // Продолжаем без memo файла
        }
    }

    private DBFField[] readStructure(DBFReader reader, Path dbfFilePath) {
        int fieldCount = reader.getFieldCount();
        DBFField[] fields = new DBFField[fieldCount];


        log.info("=== DBF Structure: {} ===", dbfFilePath.getFileName());
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = reader.getField(i);
            log.info("Field {}: {} ({}) length: {}",
                    i, fields[i].getName(), fields[i].getType().name(), fields[i].getLength());
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
