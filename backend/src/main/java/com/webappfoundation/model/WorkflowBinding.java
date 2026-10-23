package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Ties one connector-action to ONE stage, by the stage's unique {@code id} (NOT its code, which repeats
 * across workflows). Many bindings per stage = fan-out; zero = nothing runs when the order enters that stage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow_binding")
public class WorkflowBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long stageId;

    @Column(nullable = false)
    private String connectorId;

    @Column(nullable = false)
    private String actionCode;

    @Column(nullable = false)
    @Builder.Default
    private int ordinal = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "workflow_binding_params", joinColumns = @JoinColumn(name = "binding_id"))
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    @Builder.Default
    private Map<String, String> params = new HashMap<>();
}
