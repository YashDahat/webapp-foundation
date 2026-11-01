package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DPDP consent toggle for behavioural tracking + personalised offers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingConsentRequest {
    private boolean consent;
}
