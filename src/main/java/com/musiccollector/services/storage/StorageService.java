package com.musiccollector.services.storage;

import com.musiccollector.configuration.StorageProperties;
import com.musiccollector.model.exception.StorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Object storage for sleeve photos.
 *
 * The bytes live here rather than in Postgres: a database is a poor place to keep
 * multi-megabyte blobs and a worse one to stream them from.
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final MinioClient client;
    private final StorageProperties properties;

    /** Creates the bucket if it is missing. Idempotent, so it is safe on every start. */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        String bucket = properties.bucket();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created photo bucket {}", bucket);
            }
        } catch (Exception e) {
            // Deliberately not fatal: search, sync and everything a user without photos does
            // still works, and refusing to start would take all of it down over a feature
            // that may not be in use.
            log.error("Could not ensure the photo bucket {} exists", bucket, e);
        }
    }

    public void put(String key, InputStream data, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
            log.debug("Stored {} ({} bytes)", key, size);
        } catch (Exception e) {
            throw new StorageUnavailableException("upload", e);
        }
    }

    public GetObjectResponse get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new StorageUnavailableException("download", e);
        }
    }

    /** Best effort: a failed delete leaves an orphaned object, not a broken app. */
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.warn("Could not delete {} from storage", key, e);
        }
    }
}
