package com.webappfoundation.config;

import com.webappfoundation.connector.LoyaltyEarnConnector;
import com.webappfoundation.connector.LoyaltyReversalConnector;
import com.webappfoundation.model.WorkflowBinding;
import com.webappfoundation.model.WorkflowStage;
import com.webappfoundation.repository.WorkflowBindingRepository;
import com.webappfoundation.repository.WorkflowStageRepository;
import com.webappfoundation.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Binds the loyalty connectors into every workflow out-of-the-box so coins "just work": {@code loyalty.earn}
 * on each {@code onPaidEntry} stage (award + redeem-commit) and {@code loyalty.reverse} on each terminal
 * {@code CANCELLED} stage (reverse coins + points). Runs after {@link WorkflowService} seeds the default
 * workflows ({@code @Order(0)} on {@code ApplicationReadyEvent}); idempotent — never adds a duplicate binding.
 * Admins can still re-order or disable these at {@code /admin/workflow}.
 */
@Component
@Order(50)
public class LoyaltyWorkflowBindingInitializer {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyWorkflowBindingInitializer.class);
    private static final int LOYALTY_ORDINAL = 20;      // runs after the seeded email/refund bindings

    private final WorkflowStageRepository stageRepository;
    private final WorkflowBindingRepository bindingRepository;

    public LoyaltyWorkflowBindingInitializer(WorkflowStageRepository stageRepository,
                                             WorkflowBindingRepository bindingRepository) {
        this.stageRepository = stageRepository;
        this.bindingRepository = bindingRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bindLoyaltyConnectors() {
        int added = 0;
        for (WorkflowStage stage : stageRepository.findAll()) {
            if (stage.isOnPaidEntry()) {
                added += ensureBinding(stage.getId(), LoyaltyEarnConnector.ID, LoyaltyEarnConnector.LOYALTY_EARN);
            }
            if (WorkflowService.CANCELLED_CODE.equals(stage.getCode())) {
                added += ensureBinding(stage.getId(), LoyaltyReversalConnector.ID, LoyaltyReversalConnector.LOYALTY_REVERSE);
            }
        }
        if (added > 0) {
            log.info("[loyalty] bound loyalty connectors into {} workflow stage(s)", added);
        }
    }

    private int ensureBinding(Long stageId, String connectorId, String actionCode) {
        List<WorkflowBinding> existing = bindingRepository.findByStageIdOrderByOrdinal(stageId);
        boolean present = existing.stream()
                .anyMatch(b -> connectorId.equals(b.getConnectorId()) && actionCode.equals(b.getActionCode()));
        if (present) {
            return 0;
        }
        bindingRepository.save(WorkflowBinding.builder()
                .stageId(stageId).connectorId(connectorId).actionCode(actionCode)
                .ordinal(LOYALTY_ORDINAL).enabled(true)
                .build());
        return 1;
    }
}
