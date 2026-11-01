package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.AnalyticsSummaryDto;
import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.DailyBusinessLogDto;
import com.webappfoundation.dto.UpsertDailyBusinessLogRequest;
import com.webappfoundation.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin Analytics dashboard + the owner's end-of-day log. ADMIN-gated by SecurityConfig
 * ({@code /api/v1/admin/**}). The dashboard is read-only over the order spine; log writes are audited
 * inside {@link AnalyticsService}.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** The whole screen for one period (TODAY|WEEK|MONTH|YEAR) and optional outlet scope. */
    @GetMapping
    public AnalyticsSummaryDto summary(@RequestParam(defaultValue = "MONTH") String period,
                                       @RequestParam(required = false) Long branchId) {
        return analyticsService.summary(period, branchId);
    }

    // ── Daily business log (COGS / operating expenses / complaints) ──

    @GetMapping("/daily-log")
    public List<DailyBusinessLogDto> listLogs() {
        return analyticsService.listLogs();
    }

    @GetMapping("/daily-log/{date}")
    public DailyBusinessLogDto getLog(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return analyticsService.getLog(date);
    }

    /** Upsert by date (POST and PUT both save-or-update the row for {@code logDate}). */
    @PostMapping("/daily-log")
    public DailyBusinessLogDto upsert(@RequestBody UpsertDailyBusinessLogRequest request) {
        return analyticsService.upsert(request);
    }

    @PutMapping("/daily-log")
    public DailyBusinessLogDto update(@RequestBody UpsertDailyBusinessLogRequest request) {
        return analyticsService.upsert(request);
    }

    @DeleteMapping("/daily-log/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        analyticsService.deleteLog(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/daily-log/bulk-upload", consumes = "multipart/form-data")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) {
        return analyticsService.bulkImport(file);
    }
}
