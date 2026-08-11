package org.acme.foodpackaging.service.materials;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFUtils;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        DBFReader reader = null;

        try {
            File dbfFile = new File(dbfPath);
            if (!dbfFile.exists()) {
                throw new RuntimeException("DBF file not found: " + dbfPath);
            }

            log.info("Opening DBF file: " + dbfFile.getName() + " (" + dbfFile.length() + " bytes)");

            reader = new DBFReader(new FileInputStream(dbfFile));

            // Устанавливаем кодировку
            String encoding = charsetName != null ? charsetName : DEFAULT_ENCODING;
            reader.setCharactersetName(encoding);
            log.info("Using encoding: " + encoding);

            // Подключаем memo файл если есть
            if (memoPath != null) {
                File memoFile = new File(memoPath);
                if (memoFile.exists()) {
                    reader.setMemoFile(memoFile);
                    log.info("Memo file loaded: " + memoPath);
                } else {
                    log.warn("Memo file not found: " + memoPath);
                }
            }

            // Получаем структуру полей
            int fieldCount = reader.getFieldCount();
            DBFField[] fields = new DBFField[fieldCount];

            log.info("=== DBF Structure: " + dbfFile.getName() + " ===");
            for (int i = 0; i < fieldCount; i++) {
                fields[i] = reader.getField(i);
                log.info("Field " + i + ": " +
                        fields[i].getName() + " (" + fields[i].getType().name() +
                        ") length: " + fields[i].getLength());
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

                // 🔥 Передаем запись в consumer (без накопления в памяти)
                recordConsumer.accept(row);
                rowCount++;

                // Логируем прогресс каждые 1000 записей
                if (rowCount % 1000 == 0) {
                    log.info("Processed " + rowCount + " records from " + dbfFile.getName());
                }
            }

            log.info("Total records processed from " + dbfFile.getName() + ": " + rowCount);

        } catch (Exception e) {
            log.error("Error reading DBF file: " + dbfPath, e);
            throw new RuntimeException("Failed to read DBF file", e);
        } finally {
            DBFUtils.close(reader);
            log.info("DBF reader closed");
        }
    }

    /**
     * Упрощенный метод - ищет memo файл рядом с dbf
     */
    public void readDbfFileStreaming(String dbfPath, Consumer<Map<String, Object>> recordConsumer) {
        // Ищем файлы memo в той же папке
        String basePath = dbfPath.substring(0, dbfPath.lastIndexOf('.'));
        String dbtPath = basePath + ".DBT";
        String fptPath = basePath + ".FPT";

        String memoPath = null;
        File dbtFile = new File(dbtPath);
        File fptFile = new File(fptPath);

        if (dbtFile.exists()) {
            memoPath = dbtPath;
            log.info("Found DBT memo file: " + dbtPath);
        } else if (fptFile.exists()) {
            memoPath = fptPath;
            log.info("Found FPT memo file: " + fptPath);
        } else {
            log.info("No memo file found for: " + dbfPath);
        }

        readDbfFileStreaming(dbfPath, memoPath, DEFAULT_ENCODING, recordConsumer);
    }


}