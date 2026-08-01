package com.webappfoundation.exception;

/**
 * Thrown when the payment gateway cannot fulfil a request (order creation failed, signature
 * verification errored, ...). Unchecked so callers are not forced to wrap every gateway call.
 */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
