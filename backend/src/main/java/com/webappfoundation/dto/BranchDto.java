package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Public/admin view of a {@link com.webappfoundation.model.Branch}. {@code distanceKm} is set only when queried with coordinates. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDto {
    private Long id;
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
    private boolean active;
    private Double distanceKm;
    private Instant createdAt;
    private Instant updatedAt;
}
