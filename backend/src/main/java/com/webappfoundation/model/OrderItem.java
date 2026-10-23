package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One line of an {@link Order}. Uses the same polymorphic {@code (itemType, itemId)} catalog reference as
 * recommendations; {@code variantKey} (+ human {@code variantLabel}) snapshots the chosen option and is the
 * key used for inventory reservation. Prices are snapshots (re-priced server-side at placeOrder).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String itemType;

    @Column(nullable = false)
    private Long itemId;

    private String variantKey;
    private String variantLabel;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    private String imageUrl;
}
