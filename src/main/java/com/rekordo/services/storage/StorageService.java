package com.rekordo.services.storage;

import com.rekordo.configuration.StorageProperties;
import com.rekordo.model.exception.StorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.function.ObjLongConsumer;

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

    /**
     * Walks every object in the bucket, handing each key and its size to {@code visitor}.
     *
     * <p>The only way to ask what storage actually costs. The database knows what it *meant*
     * to store, which is a different number: {@link #delete} is best effort by design, so a
     * failed removal leaves bytes behind that no row accounts for. Nothing but a walk finds
     * those.
     *
     * <p>Paged by the client at 1000 keys a request, so this is one round trip per thousand
     * objects. Cheap today and linear in the bucket forever, which is why the only caller
     * runs it on a slow timer rather than on a scrape.
     */
    public void forEachObject(ObjLongConsumer<String> visitor) {
        try {
            for (Result<Item> result : client.listObjects(ListObjectsArgs.builder()
                    .bucket(properties.bucket())
                    .recursive(true)
                    .build())) {
                Item item = result.get();
                if (!item.isDir()) {
                    visitor.accept(item.objectName(), item.size());
                }
            }
        } catch (Exception e) {
            throw new StorageUnavailableException("list", e);
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
