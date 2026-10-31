package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Admin view of a {@link com.webappfoundation.model.DailyBusinessLog} end-of-day entry. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyBusinessLogDto {
    private Long id;
    private LocalDate logDate;
    private BigDecimal cogs;
    private BigDecimal operatingExpenses;
    private int complaints;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
}
