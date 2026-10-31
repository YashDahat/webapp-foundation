package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The owner's end-of-day entry for the figures the order spine cannot know by itself — cost of goods
 * sold, operating expenses and customer complaints. One row per calendar day (business-wide; outlet
 * splits are derived from orders, not entered here). Everything else on the Analytics screen (sales,
 * bills, customers, targets) is computed from paid {@link Order} rows — never typed here.
 *
 * <p>Combined with the day's net sales from orders this yields Gross/Net Profit and margins; the
 * complaint count feeds "complaints per 1,000 bills". A missing day simply means "no entry yet" and is
 * treated as zero cost/expense/complaints for that day.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "daily_business_log")
public class DailyBusinessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The business day this entry covers. Unique — at most one log per day. */
    @Column(nullable = false, unique = true)
    private LocalDate logDate;

    /** Cost of goods sold that day (purchase cost of what was sold). Feeds gross profit/margin. */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal cogs = BigDecimal.ZERO;

    /** Operating expenses that day — rent, staff, other (daily figure or a spread monthly cost). Feeds net profit/margin. */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal operatingExpenses = BigDecimal.ZERO;

    /** Number of customer complaints logged that day. Feeds "complaints per 1,000 bills". */
    @Column(nullable = false)
    @Builder.Default
    private int complaints = 0;

    /** Optional free-text note for the day. */
    @Column(columnDefinition = "text")
    private String note;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
