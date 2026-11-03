package com.webappfoundation.connector;

import com.webappfoundation.dto.OfferValidationResult;
import com.webappfoundation.service.ConnectorConfigService;
import com.webappfoundation.service.OfferService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies a discount code at checkout. A {@link QuoteConnector} in the {@code DISCOUNT} category: it reads the
 * code the customer typed (passed through {@code CheckoutContext.selections["loyalty.discount"]}), validates it
 * against the cart, and returns a negative {@code QuoteOption} for the amount off. The actual redemption is
 * committed on the paid stage ({@link LoyaltyEarnConnector} → {@code OfferService.commitRedemption}). Suppressed
 * when the discount programme is switched off (validation fails closed).
 */
@Component
public class LoyaltyDiscountConnector implements QuoteConnector {

    public static final String ID = "loyalty.discount";

    private final OfferService offerService;
    private final ConnectorConfigService configService;

    public LoyaltyDiscountConnector(OfferService offerService, ConnectorConfigService configService) {
        this.offerService = offerService;
        this.configService = configService;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Loyalty — discount code"; }
    @Override public ConnectorCategory category() { return ConnectorCategory.DISCOUNT; }
    @Override public boolean enabled() { return configService.isEnabled(ID, true); }
    @Override public Set<ConnectorAction> actions() { return Set.of(); }

    @Override
    public List<QuoteOption> quote(CheckoutContext ctx) {
        Map<String, String> selections = ctx.getSelections();
        String code = selections == null ? null : selections.get(ID);
        if (code == null || code.isBlank() || ctx.getUserId() == null) {
            return List.of();
        }
        OfferValidationResult result = offerService.validate(ctx.getUserId(), code, ctx.getSubtotal());
        if (!result.isValid() || result.getAmount() == null || result.getAmount().signum() <= 0) {
            return List.of();
        }
        return List.of(QuoteOption.builder()
                .connectorId(ID)
                .code(code)                                     // echo the client's selection so it matches
                .label("Code " + result.getCode())
                .amount(result.getAmount().negate())            // negative → a discount
                .detail("₹" + result.getAmount().toPlainString() + " off")
                .build());
    }
}
