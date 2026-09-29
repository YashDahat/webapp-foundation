package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Summary returned by every admin bulk-import endpoint: how many rows were created/skipped/failed. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadResult {
    @Builder.Default
    private int created = 0;
    @Builder.Default
    private int skipped = 0;
    @Builder.Default
    private int failed = 0;
    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RowError {
        /** 1-based row number as it appears in the file (data rows start at 2, after the header). */
        private int row;
        private String message;
    }

    public void addError(int row, String message) {
        this.errors.add(new RowError(row, message));
        this.failed++;
    }
}
