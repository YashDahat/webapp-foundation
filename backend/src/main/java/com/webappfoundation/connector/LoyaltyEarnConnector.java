package com.webappfoundation.connector;

import com.webappfoundation.model.LoyaltyEventType;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.OrderAdjustment;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.Tier;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.service.CoinService;
import com.webappfoundation.service.ConnectorConfigService;
import com.webappfoundation.service.LoyaltyEventService;
import com.webappfoundation.service.LoyaltyProfileService;
import com.webappfoundation.service.OfferService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;

/**
 * Loyalty earn + redeem-commit, bound to the order's paid ({@code onPaidEntry}) stage. On {@code loyalty.earn}
 * it awards reward coins (pending until the return window), records the PURCHASE score event, and commits any
 * coin redemption that was applied at checkout (the atomic balance decrement). Idempotent via the engine's
 * {@code WorkflowExecution} dedup plus per-order guards in {@link CoinService}.
 */
@Component
public class LoyaltyEarnConnector implements ActionConnector {

    public static final String ID = "loyalty.earn";
    public static final String LOYALTY_EARN = "loyalty.earn";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final CoinService coinService;
    private final LoyaltyEventService loyaltyEventService;
    private final OfferService offerService;
    private final LoyaltyProfileService profileService;
    private final ConnectorConfigService configService;

    public LoyaltyEarnConnector(OrderRepository orderRepository, CoinService coinService,
                                LoyaltyEventService loyaltyEventService, OfferService offerService,
                                LoyaltyProfileService profileService, ConnectorConfigService configService) {
        this.orderRepository = orderRepository;
        this.coinService = coinService;
        this.loyaltyEventService = loyaltyEventService;
        this.offerService = offerService;
        this.profileService = profileService;
        this.configService = configService;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Loyalty — earn & redeem"; }
    @Override public ConnectorCategory category() { return ConnectorCategory.OTHER; }
    @Override public boolean enabled() { return configService.isEnabled(ID, true); }
    @Override public Set<ConnectorAction> actions() { return Set.of(new ConnectorAction(LOYALTY_EARN, "Award & redeem coins")); }

    @Override
    public ConnectorResult execute(String actionCode, OrderStageContext ctx) {
        if (!LOYALTY_EARN.equals(actionCode)) {
            return ConnectorResult.fail("Unknown action " + actionCode);
        }
        Order order = orderRepository.findById(ctx.getOrderId()).orElse(null);
        if (order == null) return ConnectorResult.fail("Order not found");
        Integer userId = order.getUserId();
        if (userId == null) return ConnectorResult.ok("guest order — no loyalty");

        BigDecimal earned = coinService.earnForOrder(userId, order.getTotal(), order.getId());
        // Purchase score points (pending until the return window closes).
        loyaltyEventService.record(userId, LoyaltyEventType.PURCHASE, order.getId(),
                Map.of("amountPaid", order.getTotal() == null ? "0" : order.getTotal().toPlainString()));

        // Commit any coin redemption chosen at checkout (the loyalty.coins negative adjustment).
        BigDecimal redeemed = BigDecimal.ZERO;
        OrderAdjustment coinAdj = findAdjustment(order, CoinRedemptionConnector.ID);
        if (coinAdj != null) {
            redeemed = coinService.redeemForOrder(userId, coinAdj.getAmount().abs(), order.getId());
        }

        // Commit any discount-code redemption (atomic usedCount + Redemption row + budget spend).
        OrderAdjustment codeAdj = findAdjustment(order, LoyaltyDiscountConnector.ID);
        if (codeAdj != null) {
            offerService.commitRedemption(order, codeAdj);
        }

        // Surprise reward: 10% chance after a purchase for Silver+ customers.
        Tier tier = profileService.getOrCreate(userId).getTier();
        if (tier != null && tier.ordinal() >= Tier.SILVER.ordinal() && RANDOM.nextInt(100) < 10) {
            offerService.decideOffer(userId, OfferTrigger.SURPRISE, null);
        }

        return ConnectorResult.ok("earned=" + earned + (redeemed.signum() > 0 ? " redeemed=" + redeemed : "")
                + (codeAdj != null ? " code=" + codeAdj.getCode() : ""));
    }

    private OrderAdjustment findAdjustment(Order order, String connectorId) {
        if (order.getAdjustments() == null) return null;
        return order.getAdjustments().stream()
                .filter(a -> connectorId.equals(a.getConnectorId()) && a.getAmount() != null && a.getAmount().signum() < 0)
                .findFirst().orElse(null);
    }
}
