package com.webappfoundation.connector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The outcome of one {@link ActionConnector#execute}, recorded per invocation for audit + retry. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorResult {
    private boolean ok;
    private String detail;

    public static ConnectorResult ok(String detail) {
        return new ConnectorResult(true, detail);
    }

    public static ConnectorResult fail(String detail) {
        return new ConnectorResult(false, detail);
    }
}
