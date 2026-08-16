package com.webappfoundation.model;

/**
 * Where a gallery item belongs. WEBSITE = the main site gallery section; EVENT = photos grouped by an
 * event (carry eventName / eventDate). Kept as one enum + one table so "gallery section and events"
 * is a single, simple model.
 */
public enum GallerySection {
    WEBSITE,
    EVENT
}
