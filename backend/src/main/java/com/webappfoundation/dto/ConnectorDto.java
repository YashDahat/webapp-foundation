package com.webappfoundation.dto;

import com.webappfoundation.connector.ConnectorCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Admin view of a registered connector: its declared actions, enabled flag, and non-secret settings. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorDto {
    private String id;
    private String displayName;
    private ConnectorCategory category;
    private boolean enabled;
    private List<Action> actions;
    private Map<String, String> settings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private String code;
        private String label;
    }
}
