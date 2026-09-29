package com.webappfoundation.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an uploaded spreadsheet (.xlsx or .csv) into a list of row maps keyed by the (trimmed,
 * lower-cased) header cells of the first row. Keeps entity importers format-agnostic — inject this into
 * an entity service's {@code bulkImport(MultipartFile)} method and map each row to your entity.
 */
@Component
public class SpreadsheetParser {

    public List<Map<String, String>> parse(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean isCsv = name.endsWith(".csv") || contentType.contains("csv");
        boolean isXlsx = name.endsWith(".xlsx") || contentType.contains("spreadsheetml") || contentType.contains("excel");

        try {
            if (isCsv) {
                return parseCsv(file);
            }
            if (isXlsx) {
                return parseXlsx(file);
            }
            throw new IllegalArgumentException("Unsupported file type. Please upload a .xlsx or .csv file.");
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file: " + e.getMessage());
        }
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    String key = header == null ? "" : header.trim().toLowerCase();
                    if (key.isEmpty()) continue;
                    row.put(key, record.isSet(header) ? record.get(header) : "");
                }
                if (!isBlank(row)) rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseXlsx(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return rows;
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return rows;

            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cell == null ? "" : formatter.formatCellValue(cell).trim().toLowerCase());
            }

            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    String key = headers.get(c);
                    if (key.isEmpty()) continue;
                    Cell cell = dataRow.getCell(c);
                    row.put(key, cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                if (!isBlank(row)) rows.add(row);
            }
        }
        return rows;
    }

    private boolean isBlank(Map<String, String> row) {
        return row.values().stream().allMatch(v -> v == null || v.trim().isEmpty());
    }
}
