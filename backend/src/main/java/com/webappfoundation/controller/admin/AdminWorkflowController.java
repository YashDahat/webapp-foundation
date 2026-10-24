package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.WorkflowDto;
import com.webappfoundation.service.AuditActions;
import com.webappfoundation.service.AuditService;
import com.webappfoundation.service.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin workflow builder API: read all workflows (stages + bindings + cancellation policy) and replace a
 * workflow's stages/bindings. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/workflow")
public class AdminWorkflowController {

    private final WorkflowService workflowService;
    private final AuditService auditService;

    public AdminWorkflowController(WorkflowService workflowService, AuditService auditService) {
        this.workflowService = workflowService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<WorkflowDto> list() {
        return workflowService.getWorkflows();
    }

    @PutMapping("/{code}")
    public List<WorkflowDto> replace(@PathVariable String code, @RequestBody WorkflowDto request) {
        // An optional request.stageMapping migrates in-flight orders off removed stage codes (409 if required
        // but missing); replaceWorkflow audits the remap itself (WORKFLOW_STAGE_MIGRATED).
        workflowService.replaceWorkflow(code, request);
        auditService.record(AuditActions.WORKFLOW_UPDATED, AuditActions.SUCCESS, "WORKFLOW", code,
                "stages=" + (request.getStages() == null ? 0 : request.getStages().size()));
        return workflowService.getWorkflows();
    }
}
