package com.webappfoundation.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Object storage for uploaded media (images / short video). The default implementation is MinIO
 * (S3-compatible, self-hosted); it can be swapped for real AWS S3 by config, or a local-disk impl,
 * without touching callers. Keys are opaque, server-generated strings — a client filename is never
 * trusted as a key (no path-traversal surface).
 */
public interface StorageService {

    /** Store the file's bytes and return the opaque object key. {@code prefix} groups keys (e.g. "gallery"). */
    String store(MultipartFile file, String prefix);

    /** Open the object's bytes + serving metadata. Throws if the key is unknown. */
    StoredObject load(String key);

    /** Remove the object. No-op if it does not exist. */
    void delete(String key);
}
