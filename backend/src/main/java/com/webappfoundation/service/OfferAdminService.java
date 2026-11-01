package com.webappfoundation.service;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.CreateOfferRequest;
import com.webappfoundation.dto.OfferDto;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.Offer;
import com.webappfoundation.model.OfferStatus;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.OfferType;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.OfferRepository;
import com.webappfoundation.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin management of discount codes ({@code Offer}s): list, create a manual/public code, revoke, and
 * bulk-import public campaign codes. The engine issues personal codes automatically (Phase 3); here a
 * manager hand-creates codes — recorded with the approving staff id and audited (abuse: "staff misuse").
 * Codes avoid look-alike characters (0/O, 1/I/L).
 */
@Service
public class OfferAdminService {

    private static final String SAFE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final SpreadsheetParser spreadsheetParser;
    private final AuditService auditService;

    public OfferAdminService(OfferRepository offerRepository, UserRepository userRepository,
                             SpreadsheetParser spreadsheetParser, AuditService auditService) {
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.spreadsheetParser = spreadsheetParser;
        this.auditService = auditService;
    }

    public List<OfferDto> adminList() {
        List<Offer> offers = offerRepository.findAll();
        Map<Integer, User> users = userRepository.findAllById(
                        offers.stream().map(Offer::getUserId).filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return offers.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(o -> toDto(o, o.getUserId() == null ? null : users.get(o.getUserId())))
                .toList();
    }

    /**
     * Create a manual personal code ({@code userId} set) or a public campaign code ({@code userId} null).
     * {@code staffId} is the approving manager (from {@code @CurrentUser}).
     */
    @Transactional
    public OfferDto create(CreateOfferRequest req, Integer staffId) {
        if (req.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        boolean isPublic = req.getUserId() == null;
        String code = (req.getCode() == null || req.getCode().isBlank())
                ? generateCode(isPublic ? "SALE" : "MANUAL")
                : req.getCode().trim().toUpperCase();
        if (offerRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A code '" + code + "' already exists.");
        }
        if (req.getUserId() != null && userRepository.findById(req.getUserId()).isEmpty()) {
            throw new ResourceNotFoundException("User not found: " + req.getUserId());
        }
        int maxUses = isPublic ? (req.getMaxUses() == null ? 100 : req.getMaxUses()) : 1;
        Offer offer = Offer.builder()
                .code(code)
                .userId(req.getUserId())
                .trigger(isPublic ? OfferTrigger.FESTIVE : OfferTrigger.MANUAL)
                .type(req.getType())
                .value(req.getValue())
                .minSpend(req.getMinSpend())
                .maxDiscount(req.getMaxDiscount())
                .maxUses(maxUses)
                .usedCount(0)
                .startsAt(req.getStartsAt())
                .expiresAt(req.getExpiresAt())
                .campaignId(req.getCampaignId())
                .status(OfferStatus.ACTIVE)
                .manualApprovedBy(staffId)
                .createdAt(Instant.now())
                .build();
        Offer saved = offerRepository.save(offer);
        auditService.record(AuditActions.LOYALTY_OFFER_CREATED, AuditActions.SUCCESS, "LOYALTY_OFFER",
                String.valueOf(saved.getId()),
                "code=" + saved.getCode() + " type=" + saved.getType() + " public=" + isPublic);
        return toDto(saved, saved.getUserId() == null ? null : userRepository.findById(saved.getUserId()).orElse(null));
    }

    @Transactional
    public OfferDto revoke(Long id) {
        Offer offer = offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + id));
        offer.setStatus(OfferStatus.REVOKED);
        Offer saved = offerRepository.save(offer);
        auditService.record(AuditActions.LOYALTY_OFFER_REVOKED, AuditActions.SUCCESS, "LOYALTY_OFFER",
                String.valueOf(saved.getId()), "code=" + saved.getCode());
        return toDto(saved, null);
    }

    /**
     * Bulk-import public campaign codes. Columns: {@code code (required), type (PCT|FLAT|FREE_SHIP|GIFT|COINS),
     * value, minSpend, maxDiscount, maxUses, campaignId, expiresAt (ISO-8601)}. Duplicate codes are skipped.
     */
    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String code = row.getOrDefault("code", "").trim().toUpperCase();
            if (code.isEmpty()) {
                result.addError(rowNum, "code is required");
                continue;
            }
            OfferType type;
            try {
                type = OfferType.valueOf(row.getOrDefault("type", "").trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                result.addError(rowNum, "type must be one of PCT, FLAT, FREE_SHIP, GIFT, COINS");
                continue;
            }
            if (offerRepository.existsByCode(code)) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            offerRepository.save(Offer.builder()
                    .code(code)
                    .userId(null)
                    .trigger(OfferTrigger.FESTIVE)
                    .type(type)
                    .value(parseDecimal(row.get("value")))
                    .minSpend(parseDecimal(row.get("minspend")))
                    .maxDiscount(parseDecimal(row.get("maxdiscount")))
                    .maxUses(parseInt(row.get("maxuses"), 100))
                    .usedCount(0)
                    .campaignId(emptyToNull(row.getOrDefault("campaignid", "")))
                    .expiresAt(parseInstant(row.get("expiresat")))
                    .status(OfferStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .build());
            result.setCreated(result.getCreated() + 1);
        }
        auditService.record(AuditActions.LOYALTY_OFFER_BULK_IMPORT, AuditActions.SUCCESS, "LOYALTY_OFFER", null,
                "created=" + result.getCreated() + " skipped=" + result.getSkipped() + " failed=" + result.getFailed());
        return result;
    }

    // ── Helpers ──

    private String generateCode(String prefix) {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(prefix).append('-');
            for (int i = 0; i < 6; i++) {
                sb.append(SAFE_CHARS.charAt(RANDOM.nextInt(SAFE_CHARS.length())));
            }
            String code = sb.toString();
            if (!offerRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate a unique code");
    }

    private OfferDto toDto(Offer o, User u) {
        return OfferDto.builder()
                .id(o.getId()).code(o.getCode()).userId(o.getUserId())
                .userEmail(u == null ? null : u.getEmail())
                .trigger(o.getTrigger()).rung(o.getRung()).type(o.getType()).value(o.getValue())
                .minSpend(o.getMinSpend()).maxDiscount(o.getMaxDiscount())
                .maxUses(o.getMaxUses()).usedCount(o.getUsedCount())
                .startsAt(o.getStartsAt()).expiresAt(o.getExpiresAt())
                .campaignId(o.getCampaignId()).status(o.getStatus()).createdAt(o.getCreatedAt())
                .build();
    }

    private static String emptyToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal parseDecimal(String v) {
        if (v == null || v.isBlank()) return null;
        try { return new BigDecimal(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInt(String v, int fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static Instant parseInstant(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Instant.parse(v.trim()); } catch (Exception e) { return null; }
    }
}
