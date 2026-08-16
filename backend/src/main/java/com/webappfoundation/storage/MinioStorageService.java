package com.webappfoundation.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * S3/MinIO-backed {@link StorageService}. The bucket is created on startup if absent. Object keys are
 * server-generated ({@code <prefix>-<uuid><ext>}) — never derived from the client filename.
 */
@Service
public class MinioStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final S3Client s3;
    private final String bucket;

    public MinioStorageService(S3Client s3, @Value("${s3.bucket:media}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                createBucket();
            } else {
                throw e;
            }
        }
    }

    private void createBucket() {
        s3.createBucket(b -> b.bucket(bucket));
        log.info("[storage] Created bucket '{}'", bucket);
    }

    @Override
    public String store(MultipartFile file, String prefix) {
        String key = (prefix == null || prefix.isBlank() ? "obj" : prefix)
                + "-" + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        try {
            s3.putObject(
                    b -> b.bucket(bucket).key(key).contentType(file.getContentType()),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload for storage", e);
        }
        return key;
    }

    @Override
    public StoredObject load(String key) {
        ResponseInputStream<GetObjectResponse> in = s3.getObject(b -> b.bucket(bucket).key(key));
        GetObjectResponse meta = in.response();
        long size = meta.contentLength() == null ? 0L : meta.contentLength();
        String contentType = meta.contentType() == null ? "application/octet-stream" : meta.contentType();
        return new StoredObject(in, contentType, size);
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    /** Lowercased extension incl. dot ("photo.JPG" -> ".jpg"), or "" when absent. */
    private static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot).toLowerCase() : "";
    }
}
