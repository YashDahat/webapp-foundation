package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create/update payload for a day's end-of-day entry. {@code logDate} is required; a POST/PUT on an
 * existing date updates it (upsert by date). Money fields default to zero when omitted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertDailyBusinessLogRequest {
    private LocalDate logDate;
    private BigDecimal cogs;
    private BigDecimal operatingExpenses;
    private Integer complaints;
    private String note;
}
