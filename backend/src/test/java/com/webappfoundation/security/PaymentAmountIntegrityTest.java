package com.webappfoundation.security;

import com.webappfoundation.event.PaymentCapturedEvent;
import com.webappfoundation.model.FulfillmentType;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.PaymentMode;
import com.webappfoundation.model.PaymentState;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3 (amount binding) / finding #3 — the fenced order spine settles an order ONLY when the captured
 * amount equals the order's server-computed total. A capture for a smaller amount (e.g. a cut-price
 * gateway order opened against the order's referenceId) must NOT mark the order paid.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentAmountIntegrityTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void capturedAmountBelowTotal_doesNotSettleOrder() {
        Instant now = Instant.now();
        Order order = orderRepository.save(Order.builder()
                .userId(1)
                .fulfillmentType(FulfillmentType.DELIVERY)
                .paymentMode(PaymentMode.ONLINE)
                .workflowCode("default")
                .paymentState(PaymentState.AWAITING_PAYMENT)
                .subtotal(new BigDecimal("10000.00"))
                .total(new BigDecimal("10000.00"))
                .currency("INR")
                .referenceId("order_test_amount_mismatch")
                .createdAt(now)
                .updatedAt(now)
                .build());

        // A ₹1 capture against this ₹10000 order's referenceId must be refused.
        orderService.onPaymentCaptured(new PaymentCapturedEvent(
                this, "order_test_amount_mismatch", "pay_forged", new BigDecimal("1.00")));

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getPaymentState())
                .as("order must stay AWAITING_PAYMENT on an amount mismatch")
                .isEqualTo(PaymentState.AWAITING_PAYMENT);
    }
}
