package com.webappfoundation.service;

import com.webappfoundation.connector.builtin.EmailConnector;
import com.webappfoundation.connector.demo.DemoDeliveryConnector;
import com.webappfoundation.connector.demo.DemoRefundConnector;
import com.webappfoundation.dto.WorkflowDto;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.RefundPolicy;
import com.webappfoundation.model.Workflow;
import com.webappfoundation.model.WorkflowBinding;
import com.webappfoundation.model.WorkflowStage;
import com.webappfoundation.repository.WorkflowBindingRepository;
import com.webappfoundation.repository.WorkflowRepository;
import com.webappfoundation.repository.WorkflowStageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Owns workflow definitions: seeds the default + food workflows on first boot, enforces invariants, and
 * provides the stage lookups the order spine uses (initial / onPaidEntry / cancelled). Every lookup is
 * scoped by {@code workflowCode} → stage (never a bare code, which may repeat across workflows).
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    public static final String DEFAULT_CODE = "default";
    public static final String CANCELLED_CODE = "CANCELLED";

    private final WorkflowRepository workflowRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowBindingRepository bindingRepository;

    public WorkflowService(WorkflowRepository workflowRepository, WorkflowStageRepository stageRepository,
                           WorkflowBindingRepository bindingRepository) {
        this.workflowRepository = workflowRepository;
        this.stageRepository = stageRepository;
        this.bindingRepository = bindingRepository;
    }

    // ── Seeding ──

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        if (workflowRepository.count() > 0) {
            return;
        }
        log.info("Seeding default + food workflows");
        seedDefaultWorkflow();
        seedFoodWorkflow();
    }

    private void seedDefaultWorkflow() {
        workflowRepository.save(Workflow.builder().code(DEFAULT_CODE).label("Default").isDefault(true).build());
        // Every stage cancellable=FULL (e-commerce can cancel/return any time for a full refund).
        // PLACED (initial) is intentionally unbound — an online order fires nothing before payment.
        stage(DEFAULT_CODE, "PLACED", "Placed", 0, true, false, false, true, RefundPolicy.FULL, null);
        WorkflowStage confirmed = stage(DEFAULT_CODE, "CONFIRMED", "Confirmed", 1, false, true, false, true, RefundPolicy.FULL, null);
        WorkflowStage ofd = stage(DEFAULT_CODE, "OUT_FOR_DELIVERY", "Out for delivery", 2, false, false, false, true, RefundPolicy.FULL, null);
        stage(DEFAULT_CODE, "DELIVERED", "Delivered", 3, false, false, true, true, RefundPolicy.FULL, null);
        WorkflowStage cancelled = stage(DEFAULT_CODE, CANCELLED_CODE, "Cancelled", 4, false, false, true, false, RefundPolicy.NONE, null);

        // PLACED (initial) has NO bindings — an online order fires nothing before payment.
        bind(confirmed, EmailConnector.ID, EmailConnector.EMAIL_CUSTOMER, 0);
        bind(confirmed, EmailConnector.ID, EmailConnector.EMAIL_STORE, 1);
        bind(ofd, DemoDeliveryConnector.ID, DemoDeliveryConnector.DELIVERY_INITIATE, 0);
        bind(ofd, EmailConnector.ID, EmailConnector.EMAIL_CUSTOMER, 1);
        bind(ofd, EmailConnector.ID, EmailConnector.EMAIL_STORE, 2);
        bind(cancelled, EmailConnector.ID, EmailConnector.EMAIL_CUSTOMER, 0);
        bind(cancelled, EmailConnector.ID, EmailConnector.EMAIL_STORE, 1);
        bind(cancelled, DemoRefundConnector.ID, DemoRefundConnector.REFUND_ISSUE, 2);
        validateInvariants(DEFAULT_CODE);
    }

    private void seedFoodWorkflow() {
        workflowRepository.save(Workflow.builder().code("food").label("Food delivery").isDefault(false).build());
        // Cancellation tightens as it progresses: FULL → PARTIAL(50) → not cancellable once out for delivery.
        stage("food", "PLACED", "Placed", 0, true, false, false, true, RefundPolicy.FULL, null);
        WorkflowStage confirmed = stage("food", "CONFIRMED", "Confirmed", 1, false, true, false, true, RefundPolicy.FULL, null);
        WorkflowStage preparing = stage("food", "PREPARING", "Preparing", 2, false, false, false, true, RefundPolicy.PARTIAL, 50);
        WorkflowStage ofd = stage("food", "OUT_FOR_DELIVERY", "Out for delivery", 3, false, false, false, false, RefundPolicy.NONE, null);
        stage("food", "DELIVERED", "Delivered", 4, false, false, true, false, RefundPolicy.NONE, null);
        WorkflowStage cancelled = stage("food", CANCELLED_CODE, "Cancelled", 5, false, false, true, false, RefundPolicy.NONE, null);

        bind(confirmed, EmailConnector.ID, EmailConnector.EMAIL_CUSTOMER, 0);
        bind(confirmed, EmailConnector.ID, EmailConnector.EMAIL_STORE, 1);
        bind(preparing, EmailConnector.ID, EmailConnector.EMAIL_CUSTOMER, 0);
        bind(ofd, DemoDeliveryConnector.ID, DemoDeliveryConnector.DELIVERY_INITIATE, 0);
        bind(ofd, EmailConnector.ID, EmailConnector.EMAIL_CUSTOMER, 1);
        bind(ofd, EmailConnector.ID, EmailConnector.EMAIL_STORE, 2);
        bind(cancelled, EmailConnector.ID, EmailConnector.EMAIL_CUSTOMER, 0);
        bind(cancelled, EmailConnector.ID, EmailConnector.EMAIL_STORE, 1);
        bind(cancelled, DemoRefundConnector.ID, DemoRefundConnector.REFUND_ISSUE, 2);
        validateInvariants("food");
    }

    private WorkflowStage stage(String workflowCode, String code, String label, int ordinal, boolean initial,
                                boolean onPaidEntry, boolean terminal, boolean cancellable,
                                RefundPolicy refundPolicy, Integer refundPercent) {
        return stageRepository.save(WorkflowStage.builder()
                .workflowCode(workflowCode).code(code).label(label).ordinal(ordinal)
                .initial(initial).onPaidEntry(onPaidEntry).terminal(terminal)
                .cancellable(cancellable).refundPolicy(refundPolicy).refundPercent(refundPercent)
                .build());
    }

    private void bind(WorkflowStage stage, String connectorId, String actionCode, int ordinal) {
        bindingRepository.save(WorkflowBinding.builder()
                .stageId(stage.getId()).connectorId(connectorId).actionCode(actionCode)
                .ordinal(ordinal).enabled(true)
                .build());
    }

    // ── Invariants ──

    /** Enforced on seed + admin save: exactly one initial, ≤1 onPaidEntry, a terminal CANCELLED stage exists. */
    public void validateInvariants(String workflowCode) {
        long initial = stageRepository.countByWorkflowCodeAndInitialTrue(workflowCode);
        if (initial != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Workflow '" + workflowCode + "' must have exactly one initial stage (has " + initial + ")");
        }
        if (stageRepository.countByWorkflowCodeAndOnPaidEntryTrue(workflowCode) > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Workflow '" + workflowCode + "' may have at most one onPaidEntry stage");
        }
        if (stageRepository.findByWorkflowCodeAndCode(workflowCode, CANCELLED_CODE).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Workflow '" + workflowCode + "' must have a terminal " + CANCELLED_CODE + " stage");
        }
    }

    // ── Lookups (scoped by workflowCode) ──

    public String defaultWorkflowCode() {
        return workflowRepository.findByIsDefaultTrue().map(Workflow::getCode).orElse(DEFAULT_CODE);
    }

    /** The requested code if it exists, else the default — used when resolving an order's workflow. */
    public String resolveWorkflowCode(String requested) {
        if (requested != null && !requested.isBlank() && workflowRepository.existsByCode(requested)) {
            return requested;
        }
        return defaultWorkflowCode();
    }

    public WorkflowStage initialStage(String workflowCode) {
        return stageRepository.findByWorkflowCodeAndInitialTrue(workflowCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Workflow '" + workflowCode + "' has no initial stage"));
    }

    public WorkflowStage onPaidEntryStage(String workflowCode) {
        return stageRepository.findByWorkflowCodeAndOnPaidEntryTrue(workflowCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Workflow '" + workflowCode + "' has no onPaidEntry stage"));
    }

    public WorkflowStage cancelledStage(String workflowCode) {
        return stage(workflowCode, CANCELLED_CODE);
    }

    public WorkflowStage stage(String workflowCode, String code) {
        return stageRepository.findByWorkflowCodeAndCode(workflowCode, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown stage '" + code + "' for workflow '" + workflowCode + "'"));
    }

    public List<WorkflowStage> stages(String workflowCode) {
        return stageRepository.findByWorkflowCodeOrderByOrdinal(workflowCode);
    }

    // ── Admin read / replace (the workflow builder) ──

    public List<WorkflowDto> getWorkflows() {
        return workflowRepository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Replace a workflow's stages + bindings wholesale from the builder, then re-check invariants. Keeps the
     * same {@code code} (in-flight orders reference it). Stage codes should be preserved by the admin so
     * orders already at a stage stay valid.
     */
    @Transactional
    public void replaceWorkflow(String code, WorkflowDto dto) {
        Workflow workflow = workflowRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + code));
        if (dto.getLabel() != null && !dto.getLabel().isBlank()) {
            workflow.setLabel(dto.getLabel());
            workflowRepository.save(workflow);
        }
        List<WorkflowStage> existing = stageRepository.findByWorkflowCodeOrderByOrdinal(code);
        for (WorkflowStage s : existing) {
            bindingRepository.deleteByStageId(s.getId());
        }
        stageRepository.deleteAll(existing);
        // Force the DELETEs to run now; otherwise Hibernate orders the replacement INSERTs before the
        // DELETEs within one flush and the new stage collides on the unique (workflowCode, code) key.
        stageRepository.flush();

        int ordinal = 0;
        for (WorkflowDto.StageDto sd : dto.getStages() == null ? List.<WorkflowDto.StageDto>of() : dto.getStages()) {
            WorkflowStage saved = stageRepository.save(WorkflowStage.builder()
                    .workflowCode(code)
                    .code(sd.getCode())
                    .label(sd.getLabel() == null ? sd.getCode() : sd.getLabel())
                    .ordinal(ordinal++)
                    .initial(sd.isInitial())
                    .onPaidEntry(sd.isOnPaidEntry())
                    .terminal(sd.isTerminal())
                    .cancellable(sd.isCancellable())
                    .refundPolicy(sd.getRefundPolicy() == null ? RefundPolicy.NONE : sd.getRefundPolicy())
                    .refundPercent(sd.getRefundPercent())
                    .build());
            int bOrdinal = 0;
            for (WorkflowDto.BindingDto bd : sd.getBindings() == null ? List.<WorkflowDto.BindingDto>of() : sd.getBindings()) {
                bindingRepository.save(WorkflowBinding.builder()
                        .stageId(saved.getId())
                        .connectorId(bd.getConnectorId())
                        .actionCode(bd.getActionCode())
                        .ordinal(bOrdinal++)
                        .enabled(bd.isEnabled())
                        .params(bd.getParams() == null ? new HashMap<>() : new HashMap<>(bd.getParams()))
                        .build());
            }
        }
        validateInvariants(code);
    }

    private WorkflowDto toDto(Workflow w) {
        List<WorkflowDto.StageDto> stages = new ArrayList<>();
        for (WorkflowStage s : stageRepository.findByWorkflowCodeOrderByOrdinal(w.getCode())) {
            List<WorkflowDto.BindingDto> bindings = bindingRepository.findByStageIdOrderByOrdinal(s.getId()).stream()
                    .map(b -> WorkflowDto.BindingDto.builder()
                            .id(b.getId()).connectorId(b.getConnectorId()).actionCode(b.getActionCode())
                            .ordinal(b.getOrdinal()).enabled(b.isEnabled()).params(b.getParams())
                            .build())
                    .toList();
            stages.add(WorkflowDto.StageDto.builder()
                    .id(s.getId()).code(s.getCode()).label(s.getLabel()).ordinal(s.getOrdinal())
                    .initial(s.isInitial()).onPaidEntry(s.isOnPaidEntry()).terminal(s.isTerminal())
                    .cancellable(s.isCancellable()).refundPolicy(s.getRefundPolicy()).refundPercent(s.getRefundPercent())
                    .bindings(bindings)
                    .build());
        }
        return WorkflowDto.builder()
                .code(w.getCode()).label(w.getLabel()).isDefault(w.isDefault()).stages(stages)
                .build();
    }
}
