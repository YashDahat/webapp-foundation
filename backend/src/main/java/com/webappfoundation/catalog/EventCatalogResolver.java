package com.webappfoundation.catalog;

import com.webappfoundation.dto.CatalogItemDto;
import com.webappfoundation.model.Event;
import com.webappfoundation.model.EventTier;
import com.webappfoundation.repository.EventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.webappfoundation.catalog.CatalogConstants.EVENT;

/**
 * Maps the {@link Event} sellable to the universal {@link CatalogItemDto} ({@code itemType="EVENT"}).
 * {@code detailPath = "/events/<slug>"}; {@code cartable} is true only for a tier-less event that is on sale
 * with seats left (a tiered event needs a tier picked on the detail page). Event-specific fields (schedule,
 * sold-out) ride the {@code attributes} map, so the fenced {@code CatalogItemDto} needs no new column.
 */
@Component
public class EventCatalogResolver implements CatalogItemResolver {

    private final EventRepository eventRepository;

    public EventCatalogResolver(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public String itemType() {
        return EVENT;
    }

    @Override
    public List<CatalogItemDto> resolve(Collection<Long> ids) {
        return eventRepository.findByIdIn(ids).stream()
                .filter(Event::isActive)
                .map(this::toDto)
                .toList();
    }

    private CatalogItemDto toDto(Event e) {
        boolean hasTiers = e.getTiers() != null && !e.getTiers().isEmpty();
        boolean onSale = isOnSale(e);
        boolean soldOut = !hasSeats(e);

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("startsAt", e.getStartsAt() == null ? null : e.getStartsAt().toString());
        attrs.put("endsAt", e.getEndsAt() == null ? null : e.getEndsAt().toString());
        attrs.put("salesCloseAt", effectiveClose(e) == null ? null : effectiveClose(e).toString());
        attrs.put("soldOut", soldOut);

        return CatalogItemDto.builder()
                .itemType(EVENT)
                .itemId(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .price(e.getPrice())
                .imageUrl(e.getImageUrl())
                .images(e.getImages())
                .slug(e.getSlug())
                .detailPath("/events/" + e.getSlug())
                .category(e.getCategory())
                .active(e.isActive())
                .cartable(!hasTiers && onSale && !soldOut)
                .workflowCode(e.getWorkflowCode())
                .attributes(attrs)
                .build();
    }

    /** Sales are open while the event is active and the current time is before the effective close. */
    private boolean isOnSale(Event e) {
        Instant close = effectiveClose(e);
        return e.isActive() && (close == null || Instant.now().isBefore(close));
    }

    /** Any seats left (pool or any tier)? Unlimited (null pool, no tiers) is always true. */
    private boolean hasSeats(Event e) {
        if (e.getTiers() != null && !e.getTiers().isEmpty()) {
            return e.getTiers().stream().anyMatch(EventCatalogResolver::tierHasSeats);
        }
        if (e.getCapacity() == null) {
            return true; // unlimited
        }
        return available(e.getCapacity(), e.getReserved()) > 0;
    }

    private static boolean tierHasSeats(EventTier t) {
        if (t.getStock() == null) {
            return true; // unlimited tier
        }
        return available(t.getStock(), t.getReserved()) > 0;
    }

    /** Sales-close instant; defaults to {@code startsAt} when not explicitly set. */
    private static Instant effectiveClose(Event e) {
        return e.getSalesCloseAt() != null ? e.getSalesCloseAt() : e.getStartsAt();
    }

    private static int available(Integer stock, Integer reserved) {
        return (stock == null ? 0 : stock) - (reserved == null ? 0 : reserved);
    }
}
