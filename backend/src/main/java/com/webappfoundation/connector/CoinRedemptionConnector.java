package com.webappfoundation.connector;

import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.service.CoinService;
import com.webappfoundation.service.ConnectorConfigService;
import com.webappfoundation.service.LoyaltySettingsService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * Lets a customer pay for part of an order with reward coins at checkout. A {@link QuoteConnector} in the
 * {@code DISCOUNT} category: it offers one option — redeem up to the allowed amount — capped at
 * {@code maxCoinPctOfOrder}% of the cart and only above {@code minOrderForCoins}, never exceeding the balance.
 * The chosen option becomes a negative {@code OrderAdjustment}; the actual balance decrement happens on the
 * paid stage ({@link LoyaltyEarnConnector}). Suppressed entirely when the discount programme is switched off.
 */
@Component
public class CoinRedemptionConnector implements QuoteConnector {

    public static final String ID = "loyalty.coins";

    private final CoinService coinService;
    private final LoyaltySettingsService settingsService;
    private final ConnectorConfigService configService;

    public CoinRedemptionConnector(CoinService coinService, LoyaltySettingsService settingsService,
                                   ConnectorConfigService configService) {
        this.coinService = coinService;
        this.settingsService = settingsService;
        this.configService = configService;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Loyalty — redeem coins"; }
    @Override public ConnectorCategory category() { return ConnectorCategory.DISCOUNT; }
    @Override public boolean enabled() { return configService.isEnabled(ID, true); }
    @Override public Set<ConnectorAction> actions() { return Set.of(); }

    @Override
    public List<QuoteOption> quote(CheckoutContext ctx) {
        LoyaltySettings s = settingsService.get();
        if (!s.isDiscountsEnabled() || ctx.getUserId() == null || ctx.getSubtotal() == null) {
            return List.of();                                       // kill switch / guest / no cart
        }
        BigDecimal subtotal = ctx.getSubtotal();
        if (subtotal.compareTo(s.getMinOrderForCoins()) < 0) {
            return List.of();                                       // below the minimum order for coins
        }
        BigDecimal balance = coinService.spendable(ctx.getUserId());
        if (balance.signum() <= 0) {
            return List.of();
        }
        BigDecimal coinValue = s.getCoinValue();
        BigDecimal maxByPct = subtotal.multiply(BigDecimal.valueOf(s.getMaxCoinPctOfOrder()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        BigDecimal maxValue = balance.multiply(coinValue).min(maxByPct);
        BigDecimal coins = maxValue.divide(coinValue, 0, RoundingMode.DOWN);
        if (coins.signum() <= 0) {
            return List.of();
        }
        BigDecimal rupeesOff = coins.multiply(coinValue).setScale(2, RoundingMode.HALF_UP);
        return List.of(QuoteOption.builder()
                .connectorId(ID)
                .code(coins.toPlainString())                        // the coin count to redeem
                .label("Use " + coins.toPlainString() + " coins")
                .amount(rupeesOff.negate())                         // negative → a discount
                .detail("₹" + rupeesOff.toPlainString() + " off")
                .build());
    }
}
