package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A persisted order — the connector anchor. Batteries-included default, like {@code Product}. Its lifecycle
 * is a DB-configured {@link Workflow} of stages ({@code currentStageCode}); {@code paymentState} is a
 * separate minimal system enum for payment reconciliation. {@code shippingAddress} is a snapshot (null for
 * self-pickup). Links to the payment spine by {@code referenceId} string (no FK, per foundation rule).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user's id (User.id) — from @CurrentUser, never sent by the client. */
    @Column(nullable = false)
    private Integer userId;

    /** Short human-friendly id shown to users, e.g. ORD-2026-000123. */
    @Column(unique = true)
    private String orderNumber;

    /** Opaque payment reference (e.g. order_<id>) linking to the Payment spine. */
    private String referenceId;

    /** The fulfilling outlet (see branches). For self-pickup this is the pickup location. */
    private Long branchId;
    private String branchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentType fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMode paymentMode;

    /** The order's lifecycle, set at placeOrder from its items (default workflow if unset). */
    @Column(nullable = false)
    private String workflowCode;

    /** The business-defined workflow stage the order is in (not a fixed enum). */
    private String currentStageCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentState paymentState;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal total;

    private BigDecimal refundAmount;
    private String cancelReason;

    @Column(nullable = false)
    private String currency;

    /** Snapshot of the chosen saved address; null for self-pickup. */
    @Embedded
    private Address shippingAddress;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    @OrderColumn(name = "line_order")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_adjustment", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "adjustment_order")
    @Builder.Default
    private List<OrderAdjustment> adjustments = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
