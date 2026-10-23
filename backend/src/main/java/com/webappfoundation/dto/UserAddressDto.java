package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A saved address in the user's book. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddressDto {
    private Long id;
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
    private boolean isDefault;
    private Instant createdAt;
}
