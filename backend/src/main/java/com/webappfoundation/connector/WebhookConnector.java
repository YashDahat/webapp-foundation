package com.webappfoundation.connector;

import java.util.Map;

/** A connector that handles inbound callbacks (e.g. a courier status update) and may advance an order's stage. */
public interface WebhookConnector extends OrderConnector {

    void handleWebhook(String payload, Map<String, String> headers);
}
