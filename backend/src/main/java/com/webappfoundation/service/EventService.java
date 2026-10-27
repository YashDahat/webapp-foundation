package com.webappfoundation.service;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.EventDto;
import com.webappfoundation.dto.EventTierDto;
import com.webappfoundation.dto.UpsertEventRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.Event;
import com.webappfoundation.model.EventTier;
import com.webappfoundation.repository.BranchRepository;
import com.webappfoundation.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CRUD + bulk-import for the {@link Event} sellable. Public reads return only active events (optionally
 * filtered by category, search, and venue branch), soonest first; admin writes are audited. Seats are
 * capacity-tracked (shared pool, or per {@link EventTier}); images are media-library URLs only.
 */
@Service
public class EventService {

    /** Events run their own lifecycle by default; a blank workflowCode binds to the seeded "events" workflow. */
    private static final String DEFAULT_WORKFLOW = "events";

    private final EventRepository eventRepository;
    private final BranchRepository branchRepository;
    private final SpreadsheetParser spreadsheetParser;
    private final AuditService auditService;

    public EventService(EventRepository eventRepository, BranchRepository branchRepository,
                        SpreadsheetParser spreadsheetParser, AuditService auditService) {
        this.eventRepository = eventRepository;
        this.branchRepository = branchRepository;
        this.spreadsheetParser = spreadsheetParser;
        this.auditService = auditService;
    }

    // ── Public reads ──

    public List<EventDto> listActive(String category, String q, Long branchId) {
        List<Event> events;
        if (q != null && !q.isBlank()) {
            events = eventRepository.findByActiveTrueAndNameContainingIgnoreCase(q.trim());
        } else if (category != null && !category.isBlank()) {
            events = eventRepository.findByActiveTrueAndCategory(category.trim());
        } else {
            events = eventRepository.findByActiveTrue();
        }
        return events.stream()
                .filter(e -> offeredAtBranch(e, branchId))
                .sorted(Comparator.comparing(Event::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toDto)
                .toList();
    }

    public EventDto getById(Long id) {
        return toDto(eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id)));
    }

    public EventDto getBySlug(String slug) {
        return toDto(eventRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + slug)));
    }

    // ── Admin reads/writes ──

    public List<EventDto> adminList() {
        return eventRepository.findAll().stream()
                .sorted(Comparator.comparing(Event::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public EventDto create(UpsertEventRequest req) {
        validateUpsert(req);
        String slug = resolveSlug(req, null);
        if (eventRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An event with slug '" + slug + "' already exists.");
        }
        if (eventRepository.existsByNameIgnoreCase(req.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An event named '" + req.getName().trim() + "' already exists.");
        }
        Instant now = Instant.now();
        Event event = Event.builder().slug(slug).createdAt(now).updatedAt(now).build();
        apply(event, req);
        Event saved = eventRepository.save(event);
        auditService.record(AuditActions.EVENT_CREATED, AuditActions.SUCCESS, "EVENT",
                String.valueOf(saved.getId()), "name=" + saved.getName());
        return toDto(saved);
    }

    @Transactional
    public EventDto update(Long id, UpsertEventRequest req) {
        validateUpsert(req);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
        String slug = resolveSlug(req, event);
        if (!slug.equals(event.getSlug()) && eventRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An event with slug '" + slug + "' already exists.");
        }
        event.setSlug(slug);
        apply(event, req);
        event.setUpdatedAt(Instant.now());
        Event saved = eventRepository.save(event);
        auditService.record(AuditActions.EVENT_UPDATED, AuditActions.SUCCESS, "EVENT",
                String.valueOf(saved.getId()), "name=" + saved.getName());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
        eventRepository.delete(event);
        auditService.record(AuditActions.EVENT_DELETED, AuditActions.SUCCESS, "EVENT",
                String.valueOf(id), "name=" + event.getName());
    }

    /**
     * Bulk-import tier-less events from a .csv/.xlsx. Columns:
     * {@code name, price, category, startsAt, endsAt, capacity, venueBranchSlug, workflowCode}
     * ({@code startsAt}/{@code endsAt} ISO-8601 instants, e.g. {@code 2026-11-14T18:30:00Z}). Duplicate
     * slug/name rows are skipped; bad rows are reported.
     */
    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1; // header is row 1; data starts at 2
        for (Map<String, String> row : rows) {
            rowNum++;
            String name = row.getOrDefault("name", "").trim();
            String priceRaw = row.getOrDefault("price", "").trim();
            String startsRaw = row.getOrDefault("startsat", "").trim();
            if (name.isEmpty() || priceRaw.isEmpty() || startsRaw.isEmpty()) {
                result.addError(rowNum, "name, price and startsAt are required");
                continue;
            }
            BigDecimal price;
            try {
                price = new BigDecimal(priceRaw);
                if (price.signum() < 0) {
                    result.addError(rowNum, "price must not be negative");
                    continue;
                }
            } catch (NumberFormatException e) {
                result.addError(rowNum, "price is not a number: " + priceRaw);
                continue;
            }
            Instant startsAt;
            try {
                startsAt = Instant.parse(startsRaw);
            } catch (Exception e) {
                result.addError(rowNum, "startsAt must be an ISO-8601 instant (e.g. 2026-11-14T18:30:00Z)");
                continue;
            }
            Instant endsAt = null;
            String endsRaw = row.getOrDefault("endsat", "").trim();
            if (!endsRaw.isEmpty()) {
                try {
                    endsAt = Instant.parse(endsRaw);
                } catch (Exception e) {
                    result.addError(rowNum, "endsAt must be an ISO-8601 instant");
                    continue;
                }
            }
            Integer capacity = null;
            String capRaw = row.getOrDefault("capacity", "").trim();
            if (!capRaw.isEmpty()) {
                try {
                    capacity = Integer.parseInt(capRaw);
                    if (capacity < 0) {
                        result.addError(rowNum, "capacity must be a non-negative integer");
                        continue;
                    }
                } catch (NumberFormatException e) {
                    result.addError(rowNum, "capacity must be a non-negative integer");
                    continue;
                }
            }
            String slug = slugify(name);
            if (eventRepository.existsBySlug(slug) || eventRepository.existsByNameIgnoreCase(name)) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            Long venueBranchId = null;
            String venueSlug = row.getOrDefault("venuebranchslug", "").trim();
            if (!venueSlug.isEmpty()) {
                venueBranchId = branchRepository.findBySlug(venueSlug).map(b -> b.getId()).orElse(null);
                if (venueBranchId == null) {
                    result.addError(rowNum, "unknown branch slug: " + venueSlug);
                    continue;
                }
            }
            Instant now = Instant.now();
            eventRepository.save(Event.builder()
                    .name(name)
                    .slug(slug)
                    .price(price)
                    .category(emptyToNull(row.getOrDefault("category", "")))
                    .startsAt(startsAt)
                    .endsAt(endsAt)
                    .capacity(capacity)
                    .reserved(0)
                    .venueBranchId(venueBranchId)
                    .workflowCode(resolveWorkflow(row.getOrDefault("workflowcode", "")))
                    .active(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            result.setCreated(result.getCreated() + 1);
        }
        auditService.record(AuditActions.EVENT_BULK_IMPORT, AuditActions.SUCCESS, "EVENT", null,
                "created=" + result.getCreated() + " skipped=" + result.getSkipped() + " failed=" + result.getFailed());
        return result;
    }

    // ── Helpers ──

    private void validateUpsert(UpsertEventRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (req.getPrice() == null || req.getPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a non-negative price is required");
        }
        if (req.getStartsAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startsAt is required");
        }
        if (req.getEndsAt() != null && req.getEndsAt().isBefore(req.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt must not be before startsAt");
        }
    }

    private void apply(Event e, UpsertEventRequest req) {
        e.setName(req.getName().trim());
        e.setDescription(emptyToNull(req.getDescription()));
        e.setPrice(req.getPrice());
        e.setCategory(emptyToNull(req.getCategory()));
        e.setActive(req.getActive() == null || req.getActive());
        String wf = emptyToNull(req.getWorkflowCode());
        e.setWorkflowCode(wf == null ? DEFAULT_WORKFLOW : wf);

        e.setStartsAt(req.getStartsAt());
        e.setEndsAt(req.getEndsAt());
        e.setSalesCloseAt(req.getSalesCloseAt());
        e.setVenueBranchId(req.getVenueBranchId());
        e.setCapacity(req.getCapacity());
        if (e.getReserved() == null) {
            e.setReserved(0);
        }

        e.setImageUrl(emptyToNull(req.getImageUrl()));
        e.setImages(req.getImages() == null ? new ArrayList<>() : new ArrayList<>(req.getImages()));

        List<String> features = new ArrayList<>();
        if (req.getFeatures() != null) {
            for (String f : req.getFeatures()) {
                String t = f == null ? "" : f.trim();
                if (!t.isEmpty()) {
                    features.add(t);
                }
            }
        }
        e.setFeatures(features);

        reconcileTiers(e, req);
    }

    /** Match request tiers to existing by id (updating), add new ones, drop the rest (orphanRemoval). */
    private void reconcileTiers(Event e, UpsertEventRequest req) {
        List<EventTier> result = new ArrayList<>();
        if (req.getTiers() != null) {
            for (UpsertEventRequest.TierInput in : req.getTiers()) {
                if (in.getLabel() == null || in.getLabel().isBlank()) {
                    continue;
                }
                EventTier t = e.getTiers().stream()
                        .filter(existing -> existing.getId() != null && existing.getId().equals(in.getId()))
                        .findFirst()
                        .orElseGet(EventTier::new);
                t.setLabel(in.getLabel().trim());
                t.setSku(emptyToNull(in.getSku()));
                t.setPrice(in.getPrice());
                t.setStock(in.getStock());
                if (t.getReserved() == null) {
                    t.setReserved(0);
                }
                result.add(t);
            }
        }
        e.getTiers().clear();
        e.getTiers().addAll(result);
    }

    private boolean offeredAtBranch(Event e, Long branchId) {
        if (branchId == null || e.getVenueBranchId() == null) {
            return true; // unscoped event shows everywhere
        }
        return e.getVenueBranchId().equals(branchId);
    }

    private String resolveSlug(UpsertEventRequest req, Event existing) {
        if (req.getSlug() != null && !req.getSlug().isBlank()) {
            return slugify(req.getSlug());
        }
        if (existing != null && existing.getSlug() != null) {
            return existing.getSlug();
        }
        return slugify(req.getName());
    }

    private static String slugify(String raw) {
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "event" : slug;
    }

    private static String emptyToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /** Blank CSV workflowCode → the default "events" workflow. */
    private static String resolveWorkflow(String raw) {
        String t = emptyToNull(raw);
        return t == null ? DEFAULT_WORKFLOW : t;
    }

    private EventDto toDto(Event e) {
        boolean hasTiers = e.getTiers() != null && !e.getTiers().isEmpty();
        List<EventTierDto> tiers = e.getTiers().stream()
                .map(t -> EventTierDto.builder()
                        .id(t.getId())
                        .label(t.getLabel())
                        .sku(t.getSku())
                        .price(t.getPrice())
                        .stock(t.getStock())
                        .reserved(t.getReserved())
                        .available(t.getStock() == null ? null : available(t.getStock(), t.getReserved()))
                        .build())
                .toList();

        Integer poolAvailable = (!hasTiers && e.getCapacity() != null) ? available(e.getCapacity(), e.getReserved()) : null;
        Integer seatsLeft = computeSeatsLeft(e, hasTiers);
        Instant close = e.getSalesCloseAt() != null ? e.getSalesCloseAt() : e.getStartsAt();
        boolean windowOpen = e.isActive() && (close == null || Instant.now().isBefore(close));
        boolean soldOut = seatsLeft != null && seatsLeft <= 0;
        boolean onSale = windowOpen && !soldOut;

        String venueName = e.getVenueBranchId() == null ? null
                : branchRepository.findById(e.getVenueBranchId()).map(b -> b.getName()).orElse(null);

        return EventDto.builder()
                .id(e.getId())
                .name(e.getName())
                .slug(e.getSlug())
                .description(e.getDescription())
                .price(e.getPrice())
                .startsAt(e.getStartsAt())
                .endsAt(e.getEndsAt())
                .salesCloseAt(e.getSalesCloseAt())
                .venueBranchId(e.getVenueBranchId())
                .venueName(venueName)
                .capacity(e.getCapacity())
                .reserved(e.getReserved())
                .available(poolAvailable)
                .seatsLeft(seatsLeft)
                .onSale(onSale)
                .soldOut(soldOut)
                .tiers(tiers)
                .imageUrl(e.getImageUrl())
                .images(e.getImages())
                .features(e.getFeatures())
                .category(e.getCategory())
                .active(e.isActive())
                .workflowCode(e.getWorkflowCode())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    /** Remaining seats across the event; null when unlimited (no cap, no tiers, or any unlimited tier). */
    private Integer computeSeatsLeft(Event e, boolean hasTiers) {
        if (hasTiers) {
            int sum = 0;
            for (EventTier t : e.getTiers()) {
                if (t.getStock() == null) {
                    return null; // an unlimited tier makes the event effectively unlimited
                }
                sum += available(t.getStock(), t.getReserved());
            }
            return sum;
        }
        if (e.getCapacity() == null) {
            return null; // unlimited
        }
        return available(e.getCapacity(), e.getReserved());
    }

    private static int available(Integer stock, Integer reserved) {
        return (stock == null ? 0 : stock) - (reserved == null ? 0 : reserved);
    }
}
