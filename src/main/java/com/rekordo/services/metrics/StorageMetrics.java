package com.rekordo.services.metrics;

import com.rekordo.services.storage.StorageService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the pictures actually cost, split by which picture it is.
 *
 * <p>Measured by walking the bucket, not by summing {@code photos.byte_size}. The database
 * knows what it meant to store; the bucket knows what is being paid for, and the two drift
 * in one direction only. {@code StorageService.delete} is best effort by design, so every
 * failed removal and every upload interrupted after the object landed but before the row
 * committed leaves bytes nothing accounts for. That gap is the number worth watching, so it
 * is published as its own gauge rather than left for somebody to subtract by eye.
 *
 * <p>Two kinds, told apart by the key, because that is the whole of the naming scheme:
 * {@code avatars/<userId>} is a profile picture, {@code <userId>/<photoId>} is a sleeve
 * photo. Cover art is not here and never will be: it is fetched from MusicBrainz and Discogs
 * at display time and never stored.
 *
 * <p>Hourly, and deliberately not on the five-minute timer next door
 * ({@link CollectionMetrics}). A walk is one request per thousand objects against MinIO and
 * grows with the bucket, while these numbers move only when somebody uploads a photo. An
 * hour-old answer to "what does storage cost" is the right answer.
 */
@Component
@RequiredArgsConstructor
public class StorageMetrics {

    private static final Logger log = LoggerFactory.getLogger(StorageMetrics.class);

    /** A picture is uploaded now and then; the bill is monthly. Hourly is generous. */
    private static final long REFRESH_MS = 3_600_000;

    static final String AVATAR = "avatar";
    static final String PHOTO = "photo";

    /** What the database believes is live, to subtract from what the bucket holds. */
    private static final String LIVE_PHOTO_BYTES =
            "SELECT coalesce(sum(byte_size), 0) FROM photos "
                    + "WHERE deleted_at IS NULL AND storage_key IS NOT NULL";

    @PersistenceContext private EntityManager entityManager;

    private final MeterRegistry registry;
    private final StorageService storageService;

    /**
     * One holder per gauge, kept in a field for as long as the bean lives: Micrometer holds
     * a gauge's source weakly, and a holder that goes out of scope reads NaN.
     */
    private final Map<String, AtomicLong> values = new LinkedHashMap<>();

    @PostConstruct
    void register() {
        for (String kind : new String[] {AVATAR, PHOTO}) {
            gauge("rekordo.storage.bytes", kind);
            gauge("rekordo.storage.objects", kind);
        }
        // What the bucket holds beyond what any live row claims. Should sit at zero; a
        // number that only ever climbs means deletes are failing somewhere.
        values.put("rekordo.storage.orphan.bytes", new AtomicLong(0));
        registry.gauge(
                "rekordo.storage.orphan.bytes",
                values.get("rekordo.storage.orphan.bytes"),
                AtomicLong::doubleValue);
        refresh();
    }

    private void gauge(String name, String kind) {
        AtomicLong holder = new AtomicLong(0);
        values.put(key(name, kind), holder);
        registry.gauge(name, Tags.of("kind", kind), holder, AtomicLong::doubleValue);
    }

    private static String key(String name, String kind) {
        return name + "/" + kind;
    }

    /** Which picture a key belongs to. The prefix is the only thing that says. */
    static String kindOf(String objectKey) {
        return objectKey.startsWith("avatars/") ? AVATAR : PHOTO;
    }

    @Scheduled(fixedDelay = REFRESH_MS)
    @Transactional(readOnly = true)
    public void refresh() {
        Map<String, long[]> tally = new LinkedHashMap<>();
        tally.put(AVATAR, new long[2]);
        tally.put(PHOTO, new long[2]);
        try {
            storageService.forEachObject((objectKey, size) -> {
                long[] counters = tally.get(kindOf(objectKey));
                counters[0] += size;
                counters[1]++;
            });

            for (Map.Entry<String, long[]> entry : tally.entrySet()) {
                values.get(key("rekordo.storage.bytes", entry.getKey())).set(entry.getValue()[0]);
                values.get(key("rekordo.storage.objects", entry.getKey())).set(entry.getValue()[1]);
            }

            long live = ((Number) entityManager.createNativeQuery(LIVE_PHOTO_BYTES).getSingleResult())
                    .longValue();
            // Never negative: a photo uploaded between the walk and this query is counted by
            // the row and not by the walk, and "minus 3 MB of rubbish" is not a reading.
            values.get("rekordo.storage.orphan.bytes").set(Math.max(0, tally.get(PHOTO)[0] - live));
        } catch (RuntimeException e) {
            // The gauges keep their last reading. Zeroing them would draw as every picture
            // having been deleted, which is a far worse lie than a stale one.
            log.warn("Could not refresh storage metrics", e);
        }
    }
}
