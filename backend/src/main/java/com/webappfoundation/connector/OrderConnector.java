package com.webappfoundation.connector;

import java.util.Set;

/**
 * Base SPI every connector implements. A connector attaches an external service (delivery, tax, loyalty,
 * refunds, notifications, …) to the order workflow. Implement one or more of {@link ActionConnector},
 * {@link QuoteConnector}, {@link WebhookConnector} alongside this. Register as a Spring {@code @Component};
 * it then appears in the admin connectors list and workflow builder with no engine changes.
 */
public interface OrderConnector {

    /** Stable unique id, e.g. "shipping.delhivery", "email". */
    String id();

    String displayName();

    ConnectorCategory category();

    /** From ConnectorConfig (DB flag + env presence). Only enabled connectors participate. */
    boolean enabled();

    /** The named actions this connector can be bound to, e.g. {email.customer, email.store}. */
    Set<ConnectorAction> actions();
}
