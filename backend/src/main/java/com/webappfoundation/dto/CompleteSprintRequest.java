package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Complete the active sprint: close it, open the next (name/dates below; sensible defaults if blank), and
 * carry forward the ticked open orders ({@code carryForwardOrderIds}) — keeping their stage/history/blocked
 * state, re-pointed onto the new sprint with {@code carriedForward=true}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteSprintRequest {
    private String nextName;
    private LocalDate nextStartsOn;
    private LocalDate nextEndsOn;
    private List<Long> carryForwardOrderIds;
}
