package com.webappfoundation.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An embeddable postal address value object. Snapshotted onto {@code Order.shippingAddress} at placeOrder
 * (a copy of the chosen saved address, NOT a link) so later edits/deletes don't mutate past orders. Also
 * carried in connector checkout/stage contexts (e.g. to price delivery).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Address {
    private String contactName;
    private String phone;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private Double latitude;
    private Double longitude;
}
