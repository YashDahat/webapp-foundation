package com.webappfoundation.connector.demo;

import com.webappfoundation.connector.ActionConnector;
import com.webappfoundation.connector.ConnectorAction;
import com.webappfoundation.connector.ConnectorCategory;
import com.webappfoundation.connector.ConnectorResult;
import com.webappfoundation.connector.OrderStageContext;
import com.webappfoundation.service.ConnectorConfigService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Records a refund <b>intent</b> for an admin to settle in the Razorpay dashboard — v1's stand-in until the
 * payment spine gains {@code PaymentService.refund} (a fenced change). The {@code refund.issue} action leaves
 * {@code paymentState=REFUND_PENDING}; swapping this for a real refund connector later needs no order/workflow
 * change (the {@code refund.*} action seam is already in place). Never logs card/PII.
 */
@Component
public class DemoRefundConnector implements ActionConnector {

    public static final String ID = "refund.demo";
    public static final String REFUND_ISSUE = "refund.issue";

    private final ConnectorConfigService configService;

    public DemoRefundConnector(ConnectorConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Demo refund";
    }

    @Override
    public ConnectorCategory category() {
        return ConnectorCategory.PAYMENT;
    }

    @Override
    public boolean enabled() {
        return configService.isEnabled(ID, true);
    }

    @Override
    public Set<ConnectorAction> actions() {
        return Set.of(new ConnectorAction(REFUND_ISSUE, "Issue refund"));
    }

    @Override
    public ConnectorResult execute(String actionCode, OrderStageContext ctx) {
        if (!REFUND_ISSUE.equals(actionCode)) {
            return ConnectorResult.fail("Unknown action " + actionCode);
        }
        String amount = ctx.getParams() == null ? null : ctx.getParams().get("refundAmount");
        String currency = ctx.getParams() == null ? null : ctx.getParams().getOrDefault("currency", "INR");
        if (amount == null || amount.isBlank()) {
            return ConnectorResult.ok("No refund due (amount 0)");
        }
        // Demo: record the intent only. A real connector would call the gateway refund API here.
        return ConnectorResult.ok("Refund intent recorded: " + currency + " " + amount
                + " — settle in the Razorpay dashboard");
    }
}
