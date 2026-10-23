package com.webappfoundation.service;

import com.webappfoundation.catalog.CatalogService;
import com.webappfoundation.catalog.CatalogService.ItemRef;
import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.CatalogItemDto;
import com.webappfoundation.dto.RecommendationDto;
import com.webappfoundation.dto.UpsertRecommendationRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.inventory.InventoryService;
import com.webappfoundation.model.CatalogRecommendation;
import com.webappfoundation.model.RecommendationType;
import com.webappfoundation.repository.CatalogRecommendationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The polymorphic recommendation engine. Public reads fan {@code (itemType, itemId)} targets out to
 * {@code CatalogService} resolvers, preserve score order, and drop items a resolver omits (inactive/deleted)
 * or that aren't offered at the selected branch. Admin writes validate that a resolver exists for each type
 * and that ids resolve, reject self-references and duplicate tuples, and are audited.
 */
@Service
public class RecommendationService {

    private final CatalogRecommendationRepository repository;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final SpreadsheetParser spreadsheetParser;
    private final AuditService auditService;

    public RecommendationService(CatalogRecommendationRepository repository, CatalogService catalogService,
                                 InventoryService inventoryService, SpreadsheetParser spreadsheetParser,
                                 AuditService auditService) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.spreadsheetParser = spreadsheetParser;
        this.auditService = auditService;
    }

    // ── Public ──

    public List<CatalogItemDto> getRecommendations(String itemType, Long itemId, RecommendationType type, Long branchId) {
        if (itemType == null || itemId == null || type == null) {
            return List.of();
        }
        List<CatalogRecommendation> rows = repository
                .findBySourceItemTypeAndSourceItemIdAndRelationTypeAndActiveTrueOrderByScoreDesc(itemType, itemId, type);
        if (rows.isEmpty()) {
            return List.of();
        }
        // Resolve all targets in one batch, then re-order by the rows' score order.
        List<ItemRef> refs = rows.stream()
                .map(r -> new ItemRef(r.getTargetItemType(), r.getTargetItemId()))
                .toList();
        Map<String, CatalogItemDto> byKey = new HashMap<>();
        for (CatalogItemDto dto : catalogService.resolveMixed(refs)) {
            byKey.put(key(dto.getItemType(), dto.getItemId()), dto);
        }
        List<CatalogItemDto> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (CatalogRecommendation r : rows) {
            String k = key(r.getTargetItemType(), r.getTargetItemId());
            if (!seen.add(k)) {
                continue;
            }
            CatalogItemDto dto = byKey.get(k);
            if (dto == null) {
                continue; // resolver dropped it (inactive/deleted)
            }
            if (branchId != null && !inventoryService.availableAt(r.getTargetItemType(), r.getTargetItemId(), branchId)) {
                continue; // not offered at the selected branch
            }
            result.add(dto);
        }
        return result;
    }

    // ── Admin ──

    public List<RecommendationDto> all() {
        List<CatalogRecommendation> rows = repository.findAll();
        Map<String, String> names = resolveNames(rows);
        return rows.stream().map(r -> toDto(r, names)).toList();
    }

    @Transactional
    public RecommendationDto create(UpsertRecommendationRequest req) {
        validate(req, null);
        Instant now = Instant.now();
        CatalogRecommendation rec = CatalogRecommendation.builder()
                .sourceItemType(req.getSourceItemType())
                .sourceItemId(req.getSourceItemId())
                .targetItemType(req.getTargetItemType())
                .targetItemId(req.getTargetItemId())
                .relationType(req.getRelationType())
                .score(req.getScore() == null ? 0.0 : req.getScore())
                .active(req.getActive() == null || req.getActive())
                .createdAt(now)
                .updatedAt(now)
                .build();
        CatalogRecommendation saved = repository.save(rec);
        auditService.record(AuditActions.RECOMMENDATION_CREATED, AuditActions.SUCCESS, "RECOMMENDATION",
                String.valueOf(saved.getId()), summary(saved));
        return toDto(saved, resolveNames(List.of(saved)));
    }

    @Transactional
    public RecommendationDto update(Long id, UpsertRecommendationRequest req) {
        CatalogRecommendation rec = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + id));
        validate(req, id);
        rec.setSourceItemType(req.getSourceItemType());
        rec.setSourceItemId(req.getSourceItemId());
        rec.setTargetItemType(req.getTargetItemType());
        rec.setTargetItemId(req.getTargetItemId());
        rec.setRelationType(req.getRelationType());
        rec.setScore(req.getScore() == null ? 0.0 : req.getScore());
        rec.setActive(req.getActive() == null || req.getActive());
        rec.setUpdatedAt(Instant.now());
        CatalogRecommendation saved = repository.save(rec);
        auditService.record(AuditActions.RECOMMENDATION_UPDATED, AuditActions.SUCCESS, "RECOMMENDATION",
                String.valueOf(saved.getId()), summary(saved));
        return toDto(saved, resolveNames(List.of(saved)));
    }

    @Transactional
    public void delete(Long id) {
        CatalogRecommendation rec = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + id));
        repository.delete(rec);
        auditService.record(AuditActions.RECOMMENDATION_DELETED, AuditActions.SUCCESS, "RECOMMENDATION",
                String.valueOf(id), summary(rec));
    }

    /**
     * Bulk-import recommendations. Columns: {@code sourceItemType, sourceItemId, targetItemType,
     * targetItemId, relationType, score, active}. Validates a resolver exists for each type and that ids
     * resolve; skips self-references and duplicate tuples.
     */
    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String sourceType = row.getOrDefault("sourceitemtype", "").trim();
            String targetType = row.getOrDefault("targetitemtype", "").trim();
            String relationRaw = row.getOrDefault("relationtype", "").trim().toUpperCase();
            Long sourceId = parseLong(row.get("sourceitemid"));
            Long targetId = parseLong(row.get("targetitemid"));
            if (sourceType.isEmpty() || targetType.isEmpty() || sourceId == null || targetId == null || relationRaw.isEmpty()) {
                result.addError(rowNum, "sourceItemType, sourceItemId, targetItemType, targetItemId and relationType are required");
                continue;
            }
            RecommendationType relation;
            try {
                relation = RecommendationType.valueOf(relationRaw);
            } catch (IllegalArgumentException e) {
                result.addError(rowNum, "relationType must be UPSELL or CROSS_SELL");
                continue;
            }
            UpsertRecommendationRequest req = UpsertRecommendationRequest.builder()
                    .sourceItemType(sourceType).sourceItemId(sourceId)
                    .targetItemType(targetType).targetItemId(targetId)
                    .relationType(relation)
                    .score(parseDouble(row.get("score")))
                    .active(!row.containsKey("active") || truthy(row.get("active")))
                    .build();
            try {
                validate(req, null);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    result.setSkipped(result.getSkipped() + 1); // duplicate / self-reference
                } else {
                    result.addError(rowNum, e.getReason());
                }
                continue;
            }
            Instant now = Instant.now();
            repository.save(CatalogRecommendation.builder()
                    .sourceItemType(sourceType).sourceItemId(sourceId)
                    .targetItemType(targetType).targetItemId(targetId)
                    .relationType(relation)
                    .score(req.getScore() == null ? 0.0 : req.getScore())
                    .active(req.getActive() == null || req.getActive())
                    .createdAt(now).updatedAt(now)
                    .build());
            result.setCreated(result.getCreated() + 1);
        }
        auditService.record(AuditActions.RECOMMENDATION_BULK_IMPORT, AuditActions.SUCCESS, "RECOMMENDATION", null,
                "created=" + result.getCreated() + " skipped=" + result.getSkipped() + " failed=" + result.getFailed());
        return result;
    }

    // ── Helpers ──

    /**
     * Shared validation: types resolvable, ids exist, no self-reference, no duplicate tuple. {@code selfId}
     * is the row being updated (so editing it doesn't collide with itself); null when creating/importing.
     */
    private void validate(UpsertRecommendationRequest req, Long selfId) {
        if (req.getSourceItemType() == null || req.getSourceItemId() == null
                || req.getTargetItemType() == null || req.getTargetItemId() == null || req.getRelationType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sourceItemType, sourceItemId, targetItemType, targetItemId and relationType are required");
        }
        if (req.getSourceItemType().equals(req.getTargetItemType())
                && req.getSourceItemId().equals(req.getTargetItemId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An item cannot recommend itself");
        }
        if (!catalogService.supports(req.getSourceItemType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No resolver for itemType '" + req.getSourceItemType() + "'");
        }
        if (!catalogService.supports(req.getTargetItemType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No resolver for itemType '" + req.getTargetItemType() + "'");
        }
        if (catalogService.resolveOne(req.getSourceItemType(), req.getSourceItemId()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source item does not exist or is inactive");
        }
        if (catalogService.resolveOne(req.getTargetItemType(), req.getTargetItemId()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target item does not exist or is inactive");
        }
        repository.findBySourceItemTypeAndSourceItemIdAndTargetItemTypeAndTargetItemIdAndRelationType(
                        req.getSourceItemType(), req.getSourceItemId(),
                        req.getTargetItemType(), req.getTargetItemId(), req.getRelationType())
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "This recommendation already exists");
                });
    }

    /** Batch-resolve all source+target names across the given rows into a {@code type:id → name} map. */
    private Map<String, String> resolveNames(List<CatalogRecommendation> rows) {
        Set<ItemRef> refs = new LinkedHashSet<>();
        for (CatalogRecommendation r : rows) {
            refs.add(new ItemRef(r.getSourceItemType(), r.getSourceItemId()));
            refs.add(new ItemRef(r.getTargetItemType(), r.getTargetItemId()));
        }
        Map<String, String> names = new HashMap<>();
        for (CatalogItemDto dto : catalogService.resolveMixed(refs)) {
            names.put(key(dto.getItemType(), dto.getItemId()), dto.getName());
        }
        return names;
    }

    private RecommendationDto toDto(CatalogRecommendation r, Map<String, String> names) {
        return RecommendationDto.builder()
                .id(r.getId())
                .sourceItemType(r.getSourceItemType())
                .sourceItemId(r.getSourceItemId())
                .sourceItemName(names.get(key(r.getSourceItemType(), r.getSourceItemId())))
                .targetItemType(r.getTargetItemType())
                .targetItemId(r.getTargetItemId())
                .targetItemName(names.get(key(r.getTargetItemType(), r.getTargetItemId())))
                .relationType(r.getRelationType())
                .score(r.getScore())
                .active(r.isActive())
                .build();
    }

    private static String key(String itemType, Long itemId) {
        return itemType + ":" + itemId;
    }

    private static String summary(CatalogRecommendation r) {
        return r.getRelationType() + " " + key(r.getSourceItemType(), r.getSourceItemId())
                + " -> " + key(r.getTargetItemType(), r.getTargetItemId());
    }

    private static Long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean truthy(String v) {
        String s = v == null ? "" : v.trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }
}
