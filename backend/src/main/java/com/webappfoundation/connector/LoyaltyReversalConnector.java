package com.webappfoundation.connector;

import com.webappfoundation.model.Order;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.service.CoinService;
import com.webappfoundation.service.ConnectorConfigService;
import com.webappfoundation.service.LoyaltyEventService;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Loyalty reversal, bound to the order's cancelled/refund stage. On {@code loyalty.reverse} it pulls back the
 * order's earned coins, refunds any coins the customer redeemed on it, and reverses the purchase score points —
 * so a return/cancel never leaves rewards behind (spec: "points and coins reversed on return"). Idempotent.
 */
@Component
public class LoyaltyReversalConnector implements ActionConnector {

    public static final String ID = "loyalty.reverse";
    public static final String LOYALTY_REVERSE = "loyalty.reverse";

    private final OrderRepository orderRepository;
    private final CoinService coinService;
    private final LoyaltyEventService loyaltyEventService;
    private final ConnectorConfigService configService;

    public LoyaltyReversalConnector(OrderRepository orderRepository, CoinService coinService,
                                    LoyaltyEventService loyaltyEventService, ConnectorConfigService configService) {
        this.orderRepository = orderRepository;
        this.coinService = coinService;
        this.loyaltyEventService = loyaltyEventService;
        this.configService = configService;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Loyalty — reverse on return"; }
    @Override public ConnectorCategory category() { return ConnectorCategory.OTHER; }
    @Override public boolean enabled() { return configService.isEnabled(ID, true); }
    @Override public Set<ConnectorAction> actions() { return Set.of(new ConnectorAction(LOYALTY_REVERSE, "Reverse coins & points")); }

    @Override
    public ConnectorResult execute(String actionCode, OrderStageContext ctx) {
        if (!LOYALTY_REVERSE.equals(actionCode)) {
            return ConnectorResult.fail("Unknown action " + actionCode);
        }
        Order order = orderRepository.findById(ctx.getOrderId()).orElse(null);
        if (order == null) return ConnectorResult.fail("Order not found");
        coinService.reverseForOrder(order.getId());
        loyaltyEventService.reversePurchase(order.getUserId(), order.getId());
        return ConnectorResult.ok("coins + points reversed");
    }
}
