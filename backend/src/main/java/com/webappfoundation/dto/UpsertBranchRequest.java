package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create/update payload for the admin branch form. {@code slug} is derived from {@code name} when blank. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertBranchRequest {
    private String name;
    private String slug;
    private String phone;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private Double latitude;
    private Double longitude;
    private String googleMapsUrl;
    private String openingHours;
    private Boolean active;
}
