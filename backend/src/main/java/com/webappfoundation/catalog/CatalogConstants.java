package com.webappfoundation.catalog;

/** Shared itemType keys for the foundation's batteries-included sellables. */
public final class CatalogConstants {
    private CatalogConstants() {}

    /** itemType of the default {@link com.webappfoundation.model.Product} sellable. */
    public static final String PRODUCT = "PRODUCT";

    /** itemType of the {@link com.webappfoundation.model.Event} sellable (dated, finite-capacity). */
    public static final String EVENT = "EVENT";
}
