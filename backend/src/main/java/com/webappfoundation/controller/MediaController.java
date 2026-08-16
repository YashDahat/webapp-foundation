package com.webappfoundation.controller;

import com.webappfoundation.storage.StorageService;
import com.webappfoundation.storage.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Duration;

/** Public, read-only streaming of stored media by object key. Keeps the object store internal. */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final StorageService storage;

    public MediaController(StorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/{key}")
    public ResponseEntity<InputStreamResource> get(@PathVariable String key) {
        try {
            StoredObject object = storage.load(key);
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(object.contentType()))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
            if (object.size() > 0) {
                builder.contentLength(object.size());
            }
            return builder.body(new InputStreamResource(object.data()));
        } catch (NoSuchKeyException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
