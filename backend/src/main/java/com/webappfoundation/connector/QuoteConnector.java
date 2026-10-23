package com.webappfoundation.connector;

import java.util.List;

/** A connector that contributes priced options at checkout (synchronous), e.g. delivery or tax quotes. */
public interface QuoteConnector extends OrderConnector {

    List<QuoteOption> quote(CheckoutContext ctx);
}
