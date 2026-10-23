package com.webappfoundation.service;

import com.webappfoundation.connector.ConnectorRegistry;
import com.webappfoundation.connector.OrderConnector;
import com.webappfoundation.dto.ConnectorDto;
import com.webappfoundation.dto.ConnectorUpdateRequest;
import com.webappfoundation.model.ConnectorConfig;
import com.webappfoundation.repository.ConnectorConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;

/**
 * Admin view/toggle of registered connectors. Lists each connector with its declared actions, effective
 * enabled flag, and non-secret settings; updates persist a {@link ConnectorConfig} row. Secrets live in env
 * and are never listed or set here.
 */
@Service
public class ConnectorAdminService {

    private final ConnectorRegistry registry;
    private final ConnectorConfigRepository configRepository;
    private final ConnectorConfigService configService;
    private final AuditService auditService;

    public ConnectorAdminService(ConnectorRegistry registry, ConnectorConfigRepository configRepository,
                                 ConnectorConfigService configService, AuditService auditService) {
        this.registry = registry;
        this.configRepository = configRepository;
        this.configService = configService;
        this.auditService = auditService;
    }

    public List<ConnectorDto> list() {
        return registry.all().stream().map(this::toDto).toList();
    }

    @Transactional
    public ConnectorDto update(String id, ConnectorUpdateRequest req) {
        OrderConnector connector = registry.byId(id);
        if (connector == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No connector '" + id + "'");
        }
        ConnectorConfig config = configRepository.findByConnectorId(id)
                .orElseGet(() -> ConnectorConfig.builder().connectorId(id).enabled(connector.enabled()).build());
        if (req.getEnabled() != null) {
            config.setEnabled(req.getEnabled());
        }
        if (req.getSettings() != null) {
            config.setSettings(new HashMap<>(req.getSettings())); // non-secret only
        }
        configRepository.save(config);
        auditService.record(AuditActions.CONNECTOR_UPDATED, AuditActions.SUCCESS, "CONNECTOR", id,
                "enabled=" + config.isEnabled());
        return toDto(connector);
    }

    private ConnectorDto toDto(OrderConnector c) {
        List<ConnectorDto.Action> actions = c.actions().stream()
                .map(a -> ConnectorDto.Action.builder().code(a.getCode()).label(a.getLabel()).build())
                .toList();
        return ConnectorDto.builder()
                .id(c.id())
                .displayName(c.displayName())
                .category(c.category())
                .enabled(c.enabled())
                .actions(actions)
                .settings(configService.settings(c.id()))
                .build();
    }
}
