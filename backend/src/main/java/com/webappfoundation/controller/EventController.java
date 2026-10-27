package com.webappfoundation.controller;

import com.webappfoundation.dto.EventDto;
import com.webappfoundation.service.EventService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public event reads for the {@link com.webappfoundation.model.Event} sellable. Unauthenticated
 * ({@code anyRequest().permitAll()} in SecurityConfig). Pass {@code branchId} to return only events at that
 * venue (plus unscoped events); omit it for all events.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventDto> list(@RequestParam(required = false) String category,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) Long branchId) {
        return eventService.listActive(category, q, branchId);
    }

    @GetMapping("/{id}")
    public EventDto get(@PathVariable Long id) {
        return eventService.getById(id);
    }

    @GetMapping("/slug/{slug}")
    public EventDto getBySlug(@PathVariable String slug) {
        return eventService.getBySlug(slug);
    }
}
