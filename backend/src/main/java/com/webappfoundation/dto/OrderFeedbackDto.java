package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A submitted order rating. {@code orderNumber} is resolved for the admin grid's readability. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFeedbackDto {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private int rating;
    private String comment;
    private Instant createdAt;
}
