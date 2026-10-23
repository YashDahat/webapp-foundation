package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.ConnectorDto;
import com.webappfoundation.dto.ConnectorUpdateRequest;
import com.webappfoundation.service.ConnectorAdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin connectors API: list registered connectors (id, category, enabled, declared actions, non-secret
 * settings) and toggle enable / edit settings. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}).
 * Secrets are configured via env and never returned or set here.
 */
@RestController
@RequestMapping("/api/v1/admin/connectors")
public class AdminConnectorController {

    private final ConnectorAdminService connectorAdminService;

    public AdminConnectorController(ConnectorAdminService connectorAdminService) {
        this.connectorAdminService = connectorAdminService;
    }

    @GetMapping
    public List<ConnectorDto> list() {
        return connectorAdminService.list();
    }

    @PutMapping("/{id}")
    public ConnectorDto update(@PathVariable String id, @RequestBody ConnectorUpdateRequest request) {
        return connectorAdminService.update(id, request);
    }
}
