package com.webappfoundation.dto;

import com.webappfoundation.model.LoyaltyEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A behavioural activity beacon from the client. Only low-value engagement types are accepted
 * (PRODUCT_VIEW, WISHLIST, ADD_TO_CART, DAILY_VISIT, SHARE_CLICK); high-value events (PURCHASE, REVIEW,
 * REFERRAL_*) are recorded server-side from their real domain actions and can never be posted here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordActivityRequest {
    private LoyaltyEventType type;
    private String itemType;
    private Long itemId;
}
