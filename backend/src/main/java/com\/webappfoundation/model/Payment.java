package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment record owned entirely by the payment spine. {@code referenceId} is an opaque
 * link to whatever domain entity triggered the payment (an order id, a booking id, ...)
 * stored as a plain string — the payment layer intentionally has NO foreign key to any
 * business entity, which is what keeps it dependency-free and cycle-proof. The gateway
 * ids are provider-neutral: they hold whatever the active PaymentGateway returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque reference to the domain entity being paid for (e.g. "order_42"). */
    private String referenceId;

    private String gatewayOrderId;
    private String gatewayPaymentId;

    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private Instant createdAt;
}
