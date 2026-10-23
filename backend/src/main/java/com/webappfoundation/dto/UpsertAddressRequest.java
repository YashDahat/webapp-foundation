package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Add/edit payload for a saved address. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertAddressRequest {
    private String label;
    private String contactName;
    private String phone;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private Double latitude;
    private Double longitude;
    private Boolean isDefault;
}
