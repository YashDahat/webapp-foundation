package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin-editable, non-secret connector configuration: an enable/disable flag and free-form settings.
 * Secrets (API keys) stay in env/properties and NEVER live here or in logs/audit. Absence of a row means
 * "use the connector's own default enabled()".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "connector_config")
public class ConnectorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String connectorId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "connector_config_settings", joinColumns = @JoinColumn(name = "config_id"))
    @MapKeyColumn(name = "setting_key")
    @Column(name = "setting_value")
    @Builder.Default
    private Map<String, String> settings = new HashMap<>();
}
