package com.webappfoundation.loyalty;

import com.webappfoundation.dto.LoyaltySettingsDto;
import com.webappfoundation.dto.OfferValidationResult;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.Offer;
import com.webappfoundation.model.OfferStatus;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.OfferType;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.OrderAdjustment;
import com.webappfoundation.connector.LoyaltyDiscountConnector;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.repository.OfferRepository;
import com.webappfoundation.repository.RedemptionRepository;
import com.webappfoundation.service.LoyaltySettingsService;
import com.webappfoundation.service.OfferService;
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
 * Verifies the Phase 3 offer engine: the master kill switch and holdout block issuance, a welcome offer is
 * issued to an eligible customer, code validation enforces ownership/min-spend, and commit is atomic + idempotent.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OfferServiceTest {

    @Autowired OfferService offerService;
    @Autowired OfferRepository offerRepository;
    @Autowired RedemptionRepository redemptionRepository;
    @Autowired LoyaltyProfileRepository profileRepository;
    @Autowired LoyaltySettingsService settingsService;

    @Test
    void killSwitchBlocksIssuance() {
        LoyaltySettingsDto s = settingsService.getDto();
        s.setDiscountsEnabled(false);
        settingsService.update(s);
        assertThat(offerService.decideOffer(910001, OfferTrigger.WELCOME, null)).isNull();
    }

    @Test
    void holdoutCustomerGetsNoCode() {
        Integer userId = 910002;
        profileRepository.save(baseProfile(userId, true));          // in holdout
        assertThat(offerService.decideOffer(userId, OfferTrigger.WELCOME, null)).isNull();
    }

    @Test
    void eligibleCustomerGetsWelcomeOffer() {
        Integer userId = 910003;
        profileRepository.save(baseProfile(userId, false));
        Offer offer = offerService.decideOffer(userId, OfferTrigger.WELCOME, null);
        assertThat(offer).isNotNull();
        assertThat(offer.getTrigger()).isEqualTo(OfferTrigger.WELCOME);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACTIVE);
        assertThat(offer.getCode()).startsWith("WEL-");
    }

    @Test
    void validateEnforcesOwnershipAndMinSpend() {
        Integer owner = 910004;
        Offer offer = offerRepository.save(Offer.builder()
                .code("WB-TEST01").userId(owner).trigger(OfferTrigger.WIN_BACK).type(OfferType.PCT)
                .value(new BigDecimal("10")).minSpend(new BigDecimal("1000")).maxUses(1).usedCount(0)
                .status(OfferStatus.ACTIVE).startsAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS)).createdAt(Instant.now()).build());

        assertThat(offerService.validate(owner, offer.getCode(), new BigDecimal("2000")).getAmount())
                .isEqualByComparingTo("200");                       // 10% of 2000
        assertThat(offerService.validate(999999, offer.getCode(), new BigDecimal("2000")).isValid()).isFalse(); // not owner
        assertThat(offerService.validate(owner, offer.getCode(), new BigDecimal("500")).isValid()).isFalse();   // below min spend
    }

    @Test
    void commitIsAtomicAndIdempotent() {
        Integer owner = 910005;
        Offer offer = offerRepository.save(Offer.builder()
                .code("WB-TEST02").userId(owner).trigger(OfferTrigger.WIN_BACK).type(OfferType.PCT)
                .value(new BigDecimal("10")).minSpend(new BigDecimal("1000")).maxUses(1).usedCount(0)
                .status(OfferStatus.ACTIVE).expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .createdAt(Instant.now()).build());

        Order order = Order.builder().id(555001L).userId(owner).build();   // transient — commit uses it in-memory
        OrderAdjustment adj = OrderAdjustment.builder()
                .connectorId(LoyaltyDiscountConnector.ID).code("WB-TEST02").amount(new BigDecimal("-200")).build();

        offerService.commitRedemption(order, adj);
        offerService.commitRedemption(order, adj);                  // idempotent — second call is a no-op

        Offer after = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(after.getUsedCount()).isEqualTo(1);
        assertThat(after.getStatus()).isEqualTo(OfferStatus.USED);
        assertThat(redemptionRepository.findByOrderId(555001L)).hasSize(1);
    }

    private LoyaltyProfile baseProfile(Integer userId, boolean holdout) {
        return LoyaltyProfile.builder()
                .userId(userId).inHoldout(holdout)
                .avgOrderValue(new BigDecimal("2000")).returnRate(BigDecimal.ZERO)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
