package com.webappfoundation.loyalty;

import com.webappfoundation.model.CoinLedgerEntry;
import com.webappfoundation.model.CoinStatus;
import com.webappfoundation.repository.CoinLedgerEntryRepository;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.service.CoinService;
import com.webappfoundation.service.LoyaltyProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Phase 2 reward-coin ledger: earn is pending (not spendable), the nightly confirm makes it
 * spendable, redemption decrements atomically and refuses to overdraw, and reversal undoes both sides.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoinServiceTest {

    @Autowired CoinService coinService;
    @Autowired CoinLedgerEntryRepository coinRepository;
    @Autowired LoyaltyProfileRepository profileRepository;
    @Autowired LoyaltyProfileService profileService;

    @Test
    void earnIsPendingUntilConfirmedThenSpendable() {
        Integer userId = 800001;
        profileService.getOrCreate(userId);                         // BRONZE → 2 coins per ₹100
        BigDecimal earned = coinService.earnForOrder(userId, new BigDecimal("2000"), 7001L);
        assertThat(earned).isEqualByComparingTo("40");              // floor(2000/100 * 2)
        assertThat(coinService.spendable(userId)).isEqualByComparingTo("0");   // pending, not spendable yet

        // Age the pending entry past the return window, then confirm.
        CoinLedgerEntry pending = coinRepository.findByOrderId(7001L).get(0);
        pending.setEarnedAt(Instant.now().minus(8, ChronoUnit.DAYS));
        coinRepository.save(pending);
        coinService.confirmDueEarnings();
        assertThat(coinService.spendable(userId)).isEqualByComparingTo("40");
    }

    @Test
    void redeemDecrementsAndRefusesToOverdraw() {
        Integer userId = 800002;
        giveConfirmedCoins(userId, 30, 7101L);

        // ₹10 off at ₹0.50/coin = 20 coins.
        BigDecimal spent = coinService.redeemForOrder(userId, new BigDecimal("10"), 7102L);
        assertThat(spent).isEqualByComparingTo("20");
        assertThat(coinService.spendable(userId)).isEqualByComparingTo("10");

        // Try to redeem ₹50 (100 coins) with only 10 left → rejected, balance unchanged.
        BigDecimal tooMuch = coinService.redeemForOrder(userId, new BigDecimal("50"), 7103L);
        assertThat(tooMuch).isEqualByComparingTo("0");
        assertThat(coinService.spendable(userId)).isEqualByComparingTo("10");
    }

    @Test
    void reverseRefundsRedeemedCoins() {
        Integer userId = 800003;
        giveConfirmedCoins(userId, 40, 7201L);
        coinService.redeemForOrder(userId, new BigDecimal("10"), 7202L);   // -20 coins → balance 20
        assertThat(coinService.spendable(userId)).isEqualByComparingTo("20");

        coinService.reverseForOrder(7202L);                          // refund the redeemed coins
        assertThat(coinService.spendable(userId)).isEqualByComparingTo("40");
    }

    private void giveConfirmedCoins(Integer userId, int coins, long orderId) {
        profileService.getOrCreate(userId);
        coinRepository.save(CoinLedgerEntry.builder()
                .userId(userId).coins(BigDecimal.valueOf(coins)).reason("purchase").orderId(orderId)
                .status(CoinStatus.PENDING).earnedAt(Instant.now().minus(8, ChronoUnit.DAYS)).build());
        coinService.confirmDueEarnings();
    }
}
