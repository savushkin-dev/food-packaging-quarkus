package org.acme.foodpackaging.service.materials;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFUtils;
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

        Path dbfFilePath = Paths.get(dbfPath);
        if (!Files.exists(dbfFilePath) || !Files.isRegularFile(dbfFilePath)) {
            throw new IllegalArgumentException("DBF file not found: " + dbfPath);
        }

        log.info("Opening DBF file: {}", dbfFilePath.getFileName());

        DBFReader reader = null;
        try {
            reader = new DBFReader(new FileInputStream(dbfFilePath.toFile()));
            setupReader(reader, charsetName, memoPath);

            DBFField[] fields = readStructure(reader, dbfFilePath);
            processRecords(reader, fields, recordConsumer);

        } catch (IOException e) {
            log.error("IO error reading DBF file: {}", dbfPath, e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error reading DBF file: {}", dbfPath, e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        } finally {
            DBFUtils.close(reader);
            log.info("DBF reader closed");
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

    private String findMemoFile(String dbfPath) {
        String basePath = dbfPath.substring(0, dbfPath.lastIndexOf('.'));
        String dbtPath = basePath + ".DBT";
        String fptPath = basePath + ".FPT";

        if (Files.exists(Paths.get(dbtPath)) && Files.isRegularFile(Paths.get(dbtPath))) {
            log.info("Found DBT memo file: {}", dbtPath);
            return dbtPath;
        } else if (Files.exists(Paths.get(fptPath)) && Files.isRegularFile(Paths.get(fptPath))) {
            log.info("Found FPT memo file: {}", fptPath);
            return fptPath;
        }
        log.info("No memo file found for: {}", dbfPath);
        return null;
    }

    private void setupReader(DBFReader reader, String charsetName, String memoPath) throws IOException {
        String encoding = charsetName != null ? charsetName : DEFAULT_ENCODING;
        reader.setCharactersetName(encoding);

        if (memoPath != null && !memoPath.trim().isEmpty()) {
            Path memoFilePath = Paths.get(memoPath);
            if (Files.exists(memoFilePath) && Files.isRegularFile(memoFilePath)) {
                reader.setMemoFile(memoFilePath.toFile());
                log.info("Memo file loaded: {}", memoPath);
            } else {
                log.warn("Memo file not found: {}", memoPath);
            }
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