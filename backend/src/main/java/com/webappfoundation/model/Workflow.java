package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A named order lifecycle that products pick via {@code workflowCode}. Its ordered {@link WorkflowStage}s
 * and their {@link WorkflowBinding}s are admin-editable, so different businesses run different lifecycles
 * with no engine changes. Exactly one workflow is the default.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    /** Order-board label for the staff role that fulfils this workflow (e.g. "Rider", "Packer"); nullable. */
    private String staffRole;
}
