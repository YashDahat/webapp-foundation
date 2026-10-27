package com.webappfoundation.inventory;

import com.webappfoundation.model.Event;
import com.webappfoundation.model.EventTier;
import com.webappfoundation.repository.EventRepository;
import com.webappfoundation.repository.EventTierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static com.webappfoundation.catalog.CatalogConstants.EVENT;

/**
 * {@link InventoryProvider} for {@link Event} seats ({@code itemType="EVENT"}), mirroring
 * {@code ProductInventoryProvider}: a single <b>atomic conditional update</b>
 * ({@code SET reserved = reserved + :qty WHERE (capacity - reserved) >= :qty}) on the event pool, or on the
 * chosen {@link EventTier} row when the event is tiered — so there is <b>no oversell under concurrency</b>.
 * An unlimited event ({@code capacity == null}, no tiers) is a no-op. {@code commit} is a no-op on counts;
 * {@code release} reverses a hold (cancellation / expiry). A shortfall throws 409 (CONFLICT).
 *
 * <p>{@code variantKey} is the chosen tier's id as a string. {@code branchId} is ignored — an event's seats
 * are global to the event (its venue is fixed), not a per-branch pool.
 */
@Component
public class EventInventoryProvider implements InventoryProvider {

    private final EventRepository eventRepository;
    private final EventTierRepository tierRepository;

    public EventInventoryProvider(EventRepository eventRepository, EventTierRepository tierRepository) {
        this.eventRepository = eventRepository;
        this.tierRepository = tierRepository;
    }

    @Override
    public String itemType() {
        return EVENT;
    }

    @Override
    public boolean availableAt(Long itemId, Long branchId) {
        Event e = eventRepository.findById(itemId).orElse(null);
        if (e == null || !e.isActive()) {
            return false;
        }
        Instant close = e.getSalesCloseAt() != null ? e.getSalesCloseAt() : e.getStartsAt();
        return close == null || Instant.now().isBefore(close); // sales still open
    }

    @Override
    @Transactional
    public void reserve(Long itemId, String variantKey, Long branchId, int qty) {
        if (qty <= 0) {
            return;
        }
        Event e = eventRepository.findById(itemId)
                .orElseThrow(() -> conflict("Event no longer available"));
        Long tierId = parseTierId(variantKey);

        if (tierId != null) {
            EventTier t = findTier(e, tierId);
            if (t.getStock() == null) {
                return; // unlimited tier
            }
            if (tierRepository.reserveIfAvailable(tierId, qty) == 0) {
                throw conflict("Only " + available(t.getStock(), t.getReserved())
                        + " seat(s) left for " + e.getName() + " (" + t.getLabel() + ")");
            }
            return;
        }

        if (e.getCapacity() == null) {
            return; // unlimited
        }
        if (eventRepository.reserveIfAvailable(itemId, qty) == 0) {
            throw conflict("Only " + available(e.getCapacity(), e.getReserved())
                    + " seat(s) left for " + e.getName());
        }
    }

    @Override
    public void commit(Long itemId, String variantKey, Long branchId, int qty) {
        // No-op on counts: a held seat stays counted in `reserved` once sold (available = capacity - reserved).
    }

    @Override
    @Transactional
    public void release(Long itemId, String variantKey, Long branchId, int qty) {
        if (qty <= 0) {
            return;
        }
        Event e = eventRepository.findById(itemId).orElse(null);
        if (e == null) {
            return;
        }
        Long tierId = parseTierId(variantKey);
        if (tierId != null) {
            tierRepository.release(tierId, qty);
        } else if (e.getCapacity() != null) {
            eventRepository.release(itemId, qty);
        }
    }

    private EventTier findTier(Event e, Long tierId) {
        return e.getTiers().stream()
                .filter(t -> tierId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> conflict("Unknown ticket tier"));
    }

    private Long parseTierId(String variantKey) {
        if (variantKey == null || variantKey.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(variantKey.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int available(Integer stock, Integer reserved) {
        return (stock == null ? 0 : stock) - (reserved == null ? 0 : reserved);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
