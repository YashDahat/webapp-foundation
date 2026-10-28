package com.webappfoundation.controller;

import com.webappfoundation.dto.PaymentVerificationResponse;
import com.webappfoundation.dto.VerifyPaymentRequest;
import com.webappfoundation.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment callbacks. There is intentionally NO public create-order endpoint: a payment order is opened
 * server-side from the authoritative total (a consumer injects {@link PaymentService} and calls
 * {@code createOrder} — the fenced order spine does this in {@code OrderService.placeOrder}). Exposing
 * create-order to clients would let a caller choose the amount, so the client only ever VERIFIES a
 * completed payment ({@code /verify}, authenticated) or the gateway posts a signed {@code /webhook}.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verify(@RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verify(request));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok("ok");
    }
}
