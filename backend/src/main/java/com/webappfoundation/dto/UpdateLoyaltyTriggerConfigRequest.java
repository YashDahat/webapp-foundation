package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin edit payload for a trigger config (the trigger itself is fixed; enable/ladder/expiry tune). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLoyaltyTriggerConfigRequest {
    private Boolean enabled;
    private String rungsJson;
    private Integer expiryDays;
}
