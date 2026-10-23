package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A saved address in a user's book (Zomato-style: multiple, labelled, one default). Owned by
 * {@code Integer userId} (@CurrentUser, never from the client). Checkout picks one and the order
 * <b>snapshots</b> it into {@code Order.shippingAddress} so later edits/deletes don't mutate past orders.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_address")
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer userId;

    /** "Home" / "Work" / custom. */
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

    @Column(nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(nullable = false)
    private Instant createdAt;
}
