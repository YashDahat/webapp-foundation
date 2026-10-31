package com.webappfoundation.config;

import com.webappfoundation.gateway.DemoPaymentGateway;
import com.webappfoundation.gateway.PaymentGateway;
import com.webappfoundation.gateway.RazorpayPaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for the payment gateway strategy. This is the ONLY place that changes when
 * a provider is added — PaymentService and every consumer stay closed for modification. The
 * live provider is selected when its keys are present; otherwise the demo fallback is used.
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    @ConditionalOnExpression("'${razorpay.key.id:}'.length() > 0 and '${razorpay.key.secret:}'.length() > 0")
    public PaymentGateway razorpayPaymentGateway(
            @Value("${razorpay.key.id:}") String keyId,
            @Value("${razorpay.key.secret:}") String keySecret,
            @Value("${razorpay.webhook.secret:}") String webhookSecret,
            @Value("${razorpay.webhook.allow-unsigned:false}") boolean allowUnsigned) {
        return new RazorpayPaymentGateway(keyId, keySecret, webhookSecret, allowUnsigned);
    }

    @Bean
    @ConditionalOnMissingBean(PaymentGateway.class)
    public PaymentGateway demoPaymentGateway() {
        return new DemoPaymentGateway();
    }
}
