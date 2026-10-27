package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.CompleteSprintRequest;
import com.webappfoundation.dto.SprintDto;
import com.webappfoundation.dto.UpsertSprintRequest;
import com.webappfoundation.service.SprintService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin sprint management for the order board. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}) —
 * sprints are NOT part of the STAFF allowlist. Writes are audited inside {@link SprintService}.
 */
@RestController
@RequestMapping("/api/v1/admin/sprints")
public class AdminSprintController {

    private final SprintService sprintService;

    public AdminSprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping
    public List<SprintDto> list(@RequestParam String workflowCode) {
        return sprintService.list(workflowCode);
    }

    @PostMapping
    public SprintDto create(@RequestBody UpsertSprintRequest request) {
        request.setId(null); // POST always creates
        return sprintService.upsert(request);
    }

    @PutMapping("/{id}")
    public SprintDto update(@PathVariable Long id, @RequestBody UpsertSprintRequest request) {
        request.setId(id);
        return sprintService.upsert(request);
    }

    @PostMapping("/{id}/complete")
    public SprintDto complete(@PathVariable Long id, @RequestBody(required = false) CompleteSprintRequest request) {
        return sprintService.complete(id, request);
    }
}
