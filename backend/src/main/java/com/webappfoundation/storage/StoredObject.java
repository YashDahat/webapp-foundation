package com.webappfoundation.storage;

import java.io.InputStream;

/** A stored object's byte stream plus the metadata needed to serve it. */
public record StoredObject(InputStream data, String contentType, long size) {}
