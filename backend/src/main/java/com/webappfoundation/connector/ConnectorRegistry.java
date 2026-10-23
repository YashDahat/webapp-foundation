package com.webappfoundation.connector;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collects every {@link OrderConnector} bean and exposes lookups by id/capability. Only {@code enabled()}
 * connectors participate in quoting/dispatch. Also surfaces each connector's declared {@code actions()} so
 * the admin workflow builder can offer them as binding targets.
 */
@Component
public class ConnectorRegistry {

    private final Map<String, OrderConnector> byId;

    public ConnectorRegistry(List<OrderConnector> connectors) {
        this.byId = connectors.stream()
                .collect(Collectors.toMap(OrderConnector::id, Function.identity()));
    }

    /** The connector with this id, or null. */
    public OrderConnector byId(String id) {
        return id == null ? null : byId.get(id);
    }

    public List<OrderConnector> all() {
        return List.copyOf(byId.values());
    }

    /** Enabled quote connectors (for checkout). */
    public List<QuoteConnector> quoteConnectors() {
        return byId.values().stream()
                .filter(OrderConnector::enabled)
                .filter(c -> c instanceof QuoteConnector)
                .map(c -> (QuoteConnector) c)
                .toList();
    }

    /** Enabled action connectors (for stage dispatch). */
    public List<ActionConnector> actionConnectors() {
        return byId.values().stream()
                .filter(OrderConnector::enabled)
                .filter(c -> c instanceof ActionConnector)
                .map(c -> (ActionConnector) c)
                .toList();
    }
}
