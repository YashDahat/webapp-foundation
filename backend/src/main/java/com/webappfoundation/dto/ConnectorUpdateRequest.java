package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Admin update of a connector's non-secret config. Secrets are never set here. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorUpdateRequest {
    private Boolean enabled;
    private Map<String, String> settings;
}
