package com.webappfoundation.repository;

import com.webappfoundation.model.ConnectorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConnectorConfigRepository extends JpaRepository<ConnectorConfig, Long> {

    Optional<ConnectorConfig> findByConnectorId(String connectorId);
}
