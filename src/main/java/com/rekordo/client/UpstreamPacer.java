package com.rekordo.client;

/**
 * Spaces outbound calls so they never exceed a fixed rate.
 *
 * <p>Deliberately a blocking gate rather than a token bucket that rejects: a user waiting
 * on a barcode scan is better served by a request that takes an extra second than by one
 * that fails. Callers are already bounded by the per-IP limit at the edge, so the queue
 * behind this cannot grow without limit.
 */
public class UpstreamPacer {

    private final long minimumGapNanos;
    private long nextSlotNanos;

    public UpstreamPacer(int requestsPerSecond) {
        this(1_000_000_000L / requestsPerSecond);
    }

    private UpstreamPacer(long minimumGapNanos) {
        this.minimumGapNanos = minimumGapNanos;
        this.nextSlotNanos = System.nanoTime();
    }

    /**
     * Discogs publishes its quota per minute rather than per second — 25 anonymous, 60 with
     * a token — and neither divides into a whole number of requests per second.
     */
    public static UpstreamPacer perMinute(int requestsPerMinute) {
        return new UpstreamPacer(60_000_000_000L / requestsPerMinute);
    }

    public synchronized void awaitSlot() {
        long now = System.nanoTime();
        long waitNanos = nextSlotNanos - now;
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for an upstream slot", e);
            }
            now = System.nanoTime();
        }
        nextSlotNanos = Math.max(now, nextSlotNanos) + minimumGapNanos;
    }
}
