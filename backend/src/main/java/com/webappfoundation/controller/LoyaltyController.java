package com.webappfoundation.controller;

import com.webappfoundation.dto.CoinLedgerDto;
import com.webappfoundation.dto.LoyaltyMeDto;
import com.webappfoundation.dto.OfferValidationResult;
import com.webappfoundation.dto.RecordActivityRequest;
import com.webappfoundation.dto.TrackingConsentRequest;
import com.webappfoundation.dto.ValidateOfferRequest;
import com.webappfoundation.model.LoyaltyEventType;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.CoinService;
import com.webappfoundation.service.LoyaltyEventService;
import com.webappfoundation.service.LoyaltyProfileService;
import com.webappfoundation.service.OfferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Customer-facing loyalty endpoints (auth required; {@code @CurrentUser} is the identity). Read your own
 * score/tier/coins, set DPDP tracking consent, and post low-value behavioural activity beacons. High-value
 * events (purchase, review, referral) are recorded server-side from their real domain actions, never here.
 */
@RestController
@RequestMapping("/api/v1/loyalty")
public class LoyaltyController {

    /** The only event types a client may self-report; everything else is server-authoritative. */
    private static final Set<LoyaltyEventType> BEACON_TYPES = Set.of(
            LoyaltyEventType.PRODUCT_VIEW, LoyaltyEventType.WISHLIST, LoyaltyEventType.ADD_TO_CART,
            LoyaltyEventType.DAILY_VISIT, LoyaltyEventType.SHARE_CLICK);

    private final LoyaltyProfileService profileService;
    private final LoyaltyEventService eventService;
    private final CoinService coinService;
    private final OfferService offerService;

    public LoyaltyController(LoyaltyProfileService profileService, LoyaltyEventService eventService,
                            CoinService coinService, OfferService offerService) {
        this.profileService = profileService;
        this.eventService = eventService;
        this.coinService = coinService;
        this.offerService = offerService;
    }

    @GetMapping("/me")
    public LoyaltyMeDto me(@CurrentUser Integer userId) {
        eventService.ensureBootstrap(userId);                       // profile + one-time signup points
        return profileService.me(userId);
    }

    @GetMapping("/coins")
    public List<CoinLedgerDto> coinHistory(@CurrentUser Integer userId) {
        return coinService.history(userId);
    }

    /** Checkout "have a code?" preview — validate a typed code against the current cart subtotal. */
    @PostMapping("/offers/validate")
    public OfferValidationResult validateOffer(@CurrentUser Integer userId, @RequestBody ValidateOfferRequest request) {
        return offerService.validate(userId, request.getCode(), request.getSubtotal());
    }

    @PostMapping("/consent")
    public LoyaltyMeDto consent(@CurrentUser Integer userId, @RequestBody TrackingConsentRequest request) {
        eventService.ensureBootstrap(userId);
        return profileService.setTrackingConsent(userId, request.isConsent());
    }

    @PostMapping("/activity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> activity(@CurrentUser Integer userId, @RequestBody RecordActivityRequest request) {
        if (request.getType() == null || !BEACON_TYPES.contains(request.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported activity type");
        }
        eventService.recordActivity(userId, request.getType(), request.getItemType(), request.getItemId());
        return ResponseEntity.noContent().build();
    }
}
