package com.webappfoundation.service;

import com.webappfoundation.model.ConnectorConfig;
import com.webappfoundation.repository.ConnectorConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Reads/writes the admin-editable, non-secret {@link ConnectorConfig}. Connectors call {@link #isEnabled}
 * with their own default so an absent row means "use the code default". Secrets are never stored here.
 */
@Service
public class ConnectorConfigService {

    private final ConnectorConfigRepository repository;

    public ConnectorConfigService(ConnectorConfigRepository repository) {
        this.repository = repository;
    }

    /** The connector's effective enabled flag — the DB override if a row exists, else {@code codeDefault}. */
    public boolean isEnabled(String connectorId, boolean codeDefault) {
        return repository.findByConnectorId(connectorId).map(ConnectorConfig::isEnabled).orElse(codeDefault);
    }

    public Map<String, String> settings(String connectorId) {
        return repository.findByConnectorId(connectorId)
                .map(ConnectorConfig::getSettings)
                .orElseGet(Map::of);
    }
}
