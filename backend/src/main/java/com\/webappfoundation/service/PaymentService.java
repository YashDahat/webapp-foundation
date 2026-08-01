package com.webappfoundation.service;

import com.webappfoundation.dto.CreatePaymentRequest;
import com.webappfoundation.dto.PaymentOrderResponse;
import com.webappfoundation.dto.PaymentVerificationResponse;
import com.webappfoundation.dto.VerifyPaymentRequest;
import com.webappfoundation.event.PaymentCapturedEvent;
import com.webappfoundation.exception.PaymentGatewayException;
import com.webappfoundation.gateway.GatewayWebhookEvent;
import com.webappfoundation.gateway.PaymentGateway;
import com.webappfoundation.model.Payment;
import com.webappfoundation.model.PaymentStatus;
import com.webappfoundation.repository.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;

/**
 * Business-agnostic payment orchestration. Inject this into any service that needs to take a
 * payment — it depends on nothing in the business domain, so a consumer → PaymentService edge
 * can never close into a cycle. It talks only to the {@link PaymentGateway} abstraction, so it
 * is closed for modification: swapping or adding a provider never touches this class. The
 * consumer calls {@link #createOrder} to open an order, then {@link #verify} after checkout,
 * and updates ITS OWN entity from the returned result. For webhook-driven flows, listen for
 * {@link PaymentCapturedEvent}.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository paymentRepository,
                          ApplicationEventPublisher eventPublisher,
                          PaymentGateway gateway) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.gateway = gateway;
    }

    /** Opens a payment order and persists a CREATED record. Returns what the frontend needs. */
    public PaymentOrderResponse createOrder(CreatePaymentRequest request) {
        String currency = (request.getCurrency() == null || request.getCurrency().isBlank())
                ? "INR" : request.getCurrency();
        int amountMinor = request.getAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();

        String gatewayOrderId = gateway.createOrder(amountMinor, currency, request.getReferenceId());

        Payment payment = paymentRepository.save(Payment.builder()
                .referenceId(request.getReferenceId())
                .gatewayOrderId(gatewayOrderId)
                .amount(request.getAmount())
                .currency(currency)
                .status(PaymentStatus.CREATED)
                .createdAt(Instant.now())
                .build());

        return PaymentOrderResponse.builder()
                .gatewayOrderId(gatewayOrderId)
                .gatewayKeyId(gateway.publicKey())
                .amount(amountMinor)
                .currency(currency)
                .paymentRecordId(payment.getId())
                .build();
    }

    /** Verifies the checkout signature, records the outcome, and publishes an event on capture. */
    @Transactional
    public PaymentVerificationResponse verify(VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findByGatewayOrderId(request.getGatewayOrderId())
                .orElseThrow(() -> new PaymentGatewayException(
                        "No payment found for order " + request.getGatewayOrderId()));

        boolean verified = gateway.verifySignature(
                request.getGatewayOrderId(), request.getGatewayPaymentId(), request.getSignature());

        if (verified) {
            markCaptured(payment, request.getGatewayPaymentId());
        } else if (payment.getStatus() != PaymentStatus.CAPTURED) {
            // never downgrade an already-captured payment (e.g. a webhook beat this call)
            payment.setGatewayPaymentId(request.getGatewayPaymentId());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }

        return PaymentVerificationResponse.builder()
                .verified(verified)
                .status(payment.getStatus().name())
                .referenceId(payment.getReferenceId())
                .build();
    }

    /**
     * Reconciles a gateway webhook — the source of truth when the user closes the browser
     * before the sync verify call fires. Verifies the signature, then captures the matching
     * Payment and publishes the event. Idempotent: a payment already captured by the sync
     * verify (or a duplicate webhook delivery) produces no second event.
     */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (!gateway.verifyWebhook(payload, signature)) {
            throw new PaymentGatewayException("Invalid webhook signature");
        }
        gateway.parseWebhook(payload)
                .filter(GatewayWebhookEvent::captured)
                .ifPresent(event -> paymentRepository.findByGatewayOrderId(event.gatewayOrderId())
                        .ifPresent(payment -> markCaptured(payment, event.gatewayPaymentId())));
    }

    /**
     * Idempotent capture + event, shared by the sync verify and the async webhook. A payment
     * already CAPTURED is left untouched so no duplicate PaymentCapturedEvent is published,
     * whichever path arrives second.
     */
    private void markCaptured(Payment payment, String gatewayPaymentId) {
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return;
        }
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setStatus(PaymentStatus.CAPTURED);
        paymentRepository.save(payment);
        eventPublisher.publishEvent(new PaymentCapturedEvent(
                this, payment.getReferenceId(), gatewayPaymentId, payment.getAmount()));
    }
}
