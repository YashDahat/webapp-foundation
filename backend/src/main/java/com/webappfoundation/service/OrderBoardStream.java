package com.webappfoundation.service;

import com.webappfoundation.event.OrderBoardChangedEvent;
import com.webappfoundation.event.OrderStageEnteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The live-board push channel: holds the open {@link SseEmitter}s per {@code workflowCode} and, after a
 * board-affecting change commits, sends a lightweight {@code board} event so connected viewers refetch. It
 * reacts to both {@link OrderStageEnteredEvent} (card moved between columns) and {@link OrderBoardChangedEvent}
 * (block/resume/assign/refund/placement). It never pushes order data — only a nudge to refetch — keeping the
 * stream cheap and the React Query cache the single source of truth (polling is the fallback if a stream drops).
 */
@Component
public class OrderBoardStream {

    private static final Logger log = LoggerFactory.getLogger(OrderBoardStream.class);
    /** EventSource reconnects on its own; keep emitters bounded so a dropped client is reaped. */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, Collection<SseEmitter>> byWorkflow = new ConcurrentHashMap<>();

    /** Open a stream for one workflow's board. Caller (controller) is already security-gated. */
    public SseEmitter subscribe(String workflowCode) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Collection<SseEmitter> emitters = byWorkflow.computeIfAbsent(workflowCode, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(workflowCode, emitter));
        emitter.onTimeout(() -> { emitter.complete(); remove(workflowCode, emitter); });
        emitter.onError(e -> remove(workflowCode, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data(workflowCode));
        } catch (IOException e) {
            remove(workflowCode, emitter);
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStageEntered(OrderStageEnteredEvent event) {
        publish(event.getWorkflowCode());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBoardChanged(OrderBoardChangedEvent event) {
        publish(event.getWorkflowCode());
    }

    /** Nudge every viewer of {@code workflowCode} to refetch the board. */
    public void publish(String workflowCode) {
        Collection<SseEmitter> emitters = byWorkflow.get(workflowCode);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        String payload = Instant.now().toString();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("board").data(payload));
            } catch (Exception e) {
                remove(workflowCode, emitter);
            }
        }
    }

    private void remove(String workflowCode, SseEmitter emitter) {
        Collection<SseEmitter> emitters = byWorkflow.get(workflowCode);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
