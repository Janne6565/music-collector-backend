package com.rekordo.services.metrics;

import io.micrometer.core.instrument.MeterRegistry;
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
 * What the collection looks like, as numbers SigNoz can draw.
 *
 * <p>These are facts about the database, not about traffic: nothing in a request path
 * knows how many records exist in total, so no counter can be incremented into existence.
 * They are read on a timer instead and held in gauges.
 *
 * <p>Read on a schedule rather than when the gauge is sampled. A gauge is polled by the
 * exporter, and a dozen aggregate counts on every export interval would put a steady,
 * pointless load on Postgres -- worse, one that grows with the data it is counting. The
 * numbers move slowly; a five-minute-old answer is the right answer.
 *
 * <p>Everything is counted excluding tombstones, because a deleted record is not part of
 * anybody's collection. The one place that shows through is that the totals here will not
 * match a naive {@code SELECT count(*)}.
 */
@Component
@RequiredArgsConstructor
public class CollectionMetrics {

    private static final Logger log = LoggerFactory.getLogger(CollectionMetrics.class);

    /** Slow-moving numbers, and each refresh is a handful of aggregate scans. */
    private static final long REFRESH_MS = 300_000;

    @PersistenceContext private EntityManager entityManager;

    private final MeterRegistry registry;

    /**
     * One holder per gauge.
     *
     * <p>Micrometer holds a gauge's source by weak reference: register one over a local
     * variable and it reads NaN as soon as that variable is collected. These live as long
     * as the bean does.
     */
    private final Map<String, AtomicLong> values = new LinkedHashMap<>();

    /**
     * The whole picture in one round trip.
     *
     * <p>Deliberately one statement rather than a dozen: each of these is an aggregate over
     * a table, and issuing them separately would also let the panels disagree with each
     * other, since a sign-up landing between two queries shows up in one and not the next.
     *
     * <p>`copies` and `wishlist_items` store `created_at` as epoch milliseconds -- they are
     * synced records, and the clients own those clocks -- while `users.created_at` is an
     * ordinary timestamptz. Hence the two different shapes of "in the last day".
     */
    private static final String QUERY =
            """
            SELECT
              (SELECT count(*) FROM users)                                        AS users_total,
              (SELECT count(*) FROM users WHERE email_verified_at IS NOT NULL)     AS users_verified,
              (SELECT count(*) FROM users WHERE handle IS NOT NULL)                AS users_with_handle,
              (SELECT count(*) FROM users
                 WHERE created_at > now() - interval '1 day')                      AS signups_1d,
              (SELECT count(*) FROM users
                 WHERE created_at > now() - interval '7 days')                     AS signups_7d,
              (SELECT count(DISTINCT user_id) FROM copies WHERE deleted_at IS NULL) AS collectors,
              (SELECT count(*) FROM copies WHERE deleted_at IS NULL)               AS copies_total,
              (SELECT count(*) FROM copies
                 WHERE deleted_at IS NULL AND rating IS NOT NULL)                  AS copies_rated,
              (SELECT count(*) FROM copies
                 WHERE deleted_at IS NULL
                   AND created_at > (extract(epoch FROM now()) - 86400) * 1000)    AS copies_added_1d,
              (SELECT count(*) FROM wishlist_items WHERE deleted_at IS NULL)       AS wishes_total,
              (SELECT count(*) FROM photos WHERE deleted_at IS NULL)               AS photos_total,
              (SELECT coalesce(sum(byte_size), 0) FROM photos
                 WHERE deleted_at IS NULL)                                         AS photo_bytes,
              (SELECT count(*) FROM friendships WHERE status = 'ACCEPTED')         AS friendships_accepted,
              (SELECT count(*) FROM friendships WHERE status = 'PENDING')          AS friendships_pending,
              (SELECT count(*) FROM releases)                                      AS catalogue_releases,
              (SELECT count(*) FROM notification_devices)                          AS push_devices
            """;

    /** The gauge names, in the order the query returns them. */
    private static final String[] NAMES = {
        "rekordo.users.total",
        "rekordo.users.verified",
        "rekordo.users.with_handle",
        "rekordo.users.signups.1d",
        "rekordo.users.signups.7d",
        "rekordo.users.collecting",
        "rekordo.copies.total",
        "rekordo.copies.rated",
        "rekordo.copies.added.1d",
        "rekordo.wishlist.total",
        "rekordo.photos.total",
        "rekordo.photos.bytes",
        "rekordo.friendships.accepted",
        "rekordo.friendships.pending",
        "rekordo.catalogue.releases",
        "rekordo.push.devices",
    };

    @PostConstruct
    void register() {
        for (String name : NAMES) {
            values.put(name, registry.gauge(name, new AtomicLong(0)));
        }
        // Derived rather than queried: an average is the ratio of two numbers already here,
        // and computing it in SQL would only make it possible for the three to disagree.
        registry.gauge("rekordo.copies.per_collector", values, CollectionMetrics::copiesPerCollector);
        registry.gauge("rekordo.wishlist.per_collector", values, CollectionMetrics::wishesPerCollector);
        refresh();
    }

    private static double copiesPerCollector(Map<String, AtomicLong> values) {
        return ratio(values, "rekordo.copies.total", "rekordo.users.collecting");
    }

    private static double wishesPerCollector(Map<String, AtomicLong> values) {
        return ratio(values, "rekordo.wishlist.total", "rekordo.users.collecting");
    }

    /** Zero rather than NaN when nobody is collecting yet: a chart draws zero, NaN is a gap. */
    private static double ratio(Map<String, AtomicLong> values, String numerator, String denominator) {
        long bottom = values.get(denominator).get();
        return bottom == 0 ? 0 : (double) values.get(numerator).get() / bottom;
    }

    @Scheduled(fixedDelay = REFRESH_MS)
    @Transactional(readOnly = true)
    public void refresh() {
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery(QUERY).getSingleResult();
            int index = 0;
            for (String name : NAMES) {
                values.get(name).set(((Number) row[index++]).longValue());
            }
        } catch (RuntimeException e) {
            // The gauges keep their last reading, which is the honest thing for a snapshot
            // that failed to refresh -- and far better than zeroing them, which would draw
            // as every user having vanished.
            log.warn("Could not refresh collection metrics", e);
        }
    }
}
