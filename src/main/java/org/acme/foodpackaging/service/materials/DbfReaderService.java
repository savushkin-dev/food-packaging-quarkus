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

@Slf4j
@ApplicationScoped
public class DbfReaderService {

    private static final String DEFAULT_ENCODING = "CP866";

    /**
     * ПОТОКОВОЕ чтение DBF файла - НЕ загружает всё в память!
     * Вызывает consumer для каждой записи
     *
     * @param dbfPath путь к .dbf файлу
     * @param memoPath путь к .dbt или .fpt файлу (может быть null)
     * @param charsetName кодировка (например "CP866", "Windows-1251")
     * @param recordConsumer колбэк для обработки каждой записи
     */
    public void readDbfFileStreaming(String dbfPath, String memoPath, String charsetName,
                                     Consumer<Map<String, Object>> recordConsumer) {

        // Валидация входных параметров
        if (dbfPath == null || dbfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("DBF file path cannot be null or empty");
        }
        if (recordConsumer == null) {
            throw new IllegalArgumentException("Record consumer cannot be null");
        }

        Path dbfFilePath = Paths.get(dbfPath);

        // Проверка существования файла
        if (!Files.exists(dbfFilePath) || !Files.isRegularFile(dbfFilePath)) {
            throw new IllegalArgumentException("DBF file not found: " + dbfPath);
        }

        log.info("Opening DBF file: {}", dbfFilePath.getFileName());

        try (FileInputStream fis = new FileInputStream(dbfFilePath.toFile());
             DBFReader reader = new DBFReader(fis)) {

            // Устанавливаем кодировку
            String encoding = charsetName != null ? charsetName : DEFAULT_ENCODING;
            reader.setCharactersetName(encoding);

            // Подключаем memo файл если есть
            if (memoPath != null && !memoPath.trim().isEmpty()) {
                Path memoFilePath = Paths.get(memoPath);
                if (Files.exists(memoFilePath) && Files.isRegularFile(memoFilePath)) {
                    reader.setMemoFile(memoFilePath.toFile());
                    log.info("Memo file loaded: {}", memoPath);
                } else {
                    log.warn("Memo file not found: {}", memoPath);
                }
            }

            // Получаем структуру полей
            int fieldCount = reader.getFieldCount();
            DBFField[] fields = new DBFField[fieldCount];

            log.info("=== DBF Structure: {} ===", dbfFilePath.getFileName());
            for (int i = 0; i < fieldCount; i++) {
                fields[i] = reader.getField(i);
                log.info("Field {}: {} ({}) length: {}",
                        i,
                        fields[i].getName(),
                        fields[i].getType().name(),
                        fields[i].getLength());
            }

            // Читаем записи ПО ОДНОЙ и сразу передаем в callback
            Object[] rowData;
            int rowCount = 0;

            while ((rowData = reader.nextRecord()) != null) {
                Map<String, Object> row = new LinkedHashMap<>();

                for (int i = 0; i < fieldCount; i++) {
                    String fieldName = fields[i].getName();
                    Object value = rowData[i];
                    row.put(fieldName, value != null ? value : null);
                }

                recordConsumer.accept(row);
                rowCount++;
            }

            log.info("Total records processed from {}: {}", dbfFilePath.getFileName(), rowCount);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("IO error reading DBF file: {}", dbfPath, e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error reading DBF file: {}", dbfPath, e);
            throw new RuntimeException("Failed to read DBF file: " + e.getMessage(), e);
        }
    }

    /**
     * Упрощенный метод - ищет memo файл рядом с dbf
     */
    public void readDbfFileStreaming(String dbfPath, Consumer<Map<String, Object>> recordConsumer) {
        if (dbfPath == null || dbfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("DBF file path cannot be null or empty");
        }

        // Проверяем существование основного файла
        Path dbfFilePath = Paths.get(dbfPath);
        if (!Files.exists(dbfFilePath) || !Files.isRegularFile(dbfFilePath)) {
            throw new IllegalArgumentException("DBF file not found: " + dbfPath);
        }

        // Ищем файлы memo в той же папке
        String basePath = dbfPath.substring(0, dbfPath.lastIndexOf('.'));
        String dbtPath = basePath + ".DBT";
        String fptPath = basePath + ".FPT";

        String memoPath = null;
        Path dbtFilePath = Paths.get(dbtPath);
        Path fptFilePath = Paths.get(fptPath);

        if (Files.exists(dbtFilePath) && Files.isRegularFile(dbtFilePath)) {
            memoPath = dbtPath;
            log.info("Found DBT memo file: {}", dbtPath);
        } else if (Files.exists(fptFilePath) && Files.isRegularFile(fptFilePath)) {
            memoPath = fptPath;
            log.info("Found FPT memo file: {}", fptPath);
        } else {
            log.info("No memo file found for: {}", dbfPath);
        }

        readDbfFileStreaming(dbfPath, memoPath, DEFAULT_ENCODING, recordConsumer);
    }
}