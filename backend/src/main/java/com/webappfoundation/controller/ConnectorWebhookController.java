package com.webappfoundation.controller;

import com.webappfoundation.connector.ConnectorRegistry;
import com.webappfoundation.connector.OrderConnector;
import com.webappfoundation.connector.WebhookConnector;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Inbound connector callbacks: {@code POST /api/v1/connectors/{connectorId}/webhook} (public, like the
 * payment webhook — each connector verifies its own signature). Dispatches to the connector's
 * {@link WebhookConnector#handleWebhook}, which typically advances the order's stage via the engine
 * (idempotent — a redelivered webhook targeting an already-passed stage is a no-op).
 */
@RestController
@RequestMapping("/api/v1/connectors")
public class ConnectorWebhookController {

    private final ConnectorRegistry connectorRegistry;

    public ConnectorWebhookController(ConnectorRegistry connectorRegistry) {
        this.connectorRegistry = connectorRegistry;
    }

    @PostMapping("/{connectorId}/webhook")
    public ResponseEntity<Void> webhook(@PathVariable String connectorId,
                                        @RequestBody(required = false) String payload,
                                        HttpServletRequest request) {
        OrderConnector connector = connectorRegistry.byId(connectorId);
        if (!(connector instanceof WebhookConnector webhookConnector)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No webhook connector '" + connectorId + "'");
        }
        if (!connector.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector '" + connectorId + "' is disabled");
        }
        webhookConnector.handleWebhook(payload == null ? "" : payload, headers(request));
        return ResponseEntity.ok().build();
    }

    private Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }
}
