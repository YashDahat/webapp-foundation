package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The single global settings row for the Discount &amp; Loyalty programme (always {@code id = 1}). Holds the
 * client-controllable master {@code discountsEnabled} kill switch and every tunable knob from the design
 * spec. When {@code discountsEnabled} is false the whole discount/promotion side is suppressed (no offers
 * issued, no promo emails, no code/coin redemption) while loyalty scoring + tiers keep running.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_settings")
public class LoyaltySettings {

    /** Always 1 — this is a singleton row. */
    @Id
    @Builder.Default
    private Long id = 1L;

    /**
     * Master kill switch for the discount/promotion side. {@code true} = programme live;
     * {@code false} = keep calculating the Loyalty Score + tiers, but issue NO discount codes/offers,
     * send NO promotion emails, and reject code/coin redemption at checkout.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean discountsEnabled = true;

    /** Rupee value of one coin (₹0.50 → Bronze 1% back, Platinum 2%). */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal coinValue = new BigDecimal("0.50");

    /** Max share of an order coins may pay for (percent). */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxCoinPctOfOrder = 10;

    /** Minimum order subtotal (₹) before coins may be redeemed. */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal minOrderForCoins = new BigDecimal("500");

    @Column(nullable = false)
    @Builder.Default
    private Integer coinExpiryMonths = 12;

    @Column(nullable = false)
    @Builder.Default
    private Integer scoreHalfLifeDays = 90;

    /** Combined daily points cap on all non-purchase actions. */
    @Column(nullable = false)
    @Builder.Default
    private Integer dailyCap = 30;

    /** Combined monthly points cap on all non-purchase actions. */
    @Column(nullable = false)
    @Builder.Default
    private Integer monthlyCap = 300;

    /** Fixed random share of customers (percent) held out from triggered codes, for measurement. */
    @Column(nullable = false)
    @Builder.Default
    private Integer holdoutPct = 10;

    /** Target discount spend as a share of gross sales (percent) — sizes the monthly budget. */
    @Column(nullable = false)
    @Builder.Default
    private Integer discountPctTarget = 8;

    /** Customers with a return rate above this (percent, trailing window) get no codes. */
    @Column(nullable = false)
    @Builder.Default
    private Integer returnRateBlockPct = 40;

    /** No new personal code within this many days of the last one used. */
    @Column(nullable = false)
    @Builder.Default
    private Integer newCodeCooldownDays = 21;

    /** Days after delivery before purchase points/coins confirm (return window). */
    @Column(nullable = false)
    @Builder.Default
    private Integer returnWindowDays = 7;

    /** Gross-margin floor (percent) a discount may never breach. */
    @Column(nullable = false)
    @Builder.Default
    private Integer marginFloorPct = 35;
}
