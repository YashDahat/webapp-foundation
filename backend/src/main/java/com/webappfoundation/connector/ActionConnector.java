package com.webappfoundation.connector;

/** A connector that runs a bound action when an order enters a stage (invoked async by the dispatcher). */
public interface ActionConnector extends OrderConnector {

    ConnectorResult execute(String actionCode, OrderStageContext ctx);
}
