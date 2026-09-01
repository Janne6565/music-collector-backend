package com.rekordo.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory caches for the metadata proxy.
 *
 * <p>Every entry here stands in front of a catalogue that paces us: MusicBrainz allows one
 * request a second and Discogs sixty a minute, and {@code UpstreamPacer} enforces that by
 * blocking the request thread. So a cache miss is not merely slower than a hit, it is time
 * during which nobody else's search can proceed either. Measured cold against production:
 * a discography took 2.7 to 4.6 seconds and a barcode 7.9 in the worst case.
 *
 * <p>Sized and expired per catalogue rather than from one shared pool, because the four
 * questions have very different shapes -- see each constant.
 */
@Configuration
public class CacheConfig {

    /** Search queries are long-tail, so cache many for a short while. */
    public static final String METADATA_SEARCH = "metadataSearch";

    /** One artist's records, per type tab. Changes when they release something, so: a day. */
    public static final String ARTIST_DISCOGRAPHY = "artistDiscography";

    /**
     * The pressings of one album.
     *
     * <p>Small and slow-moving: pressings are added to Discogs long after a record is out,
     * and a day-old list of them is not wrong in any way a collector would notice.
     */
    public static final String ALBUM_PRESSINGS = "albumPressings";

    /**
     * Barcode to record.
     *
     * <p>The most stable question of the four -- the digits on a sleeve identify the same
     * pressing forever -- and the one a scanning session asks repeatedly.
     */
    public static final String BARCODE_LOOKUP = "barcodeLookup";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofHours(6)));
        manager.registerCustomCache(METADATA_SEARCH, spec(2_000, Duration.ofHours(6)));
        manager.registerCustomCache(ARTIST_DISCOGRAPHY, spec(2_000, Duration.ofHours(24)));
        manager.registerCustomCache(ALBUM_PRESSINGS, spec(5_000, Duration.ofHours(24)));
        manager.registerCustomCache(BARCODE_LOOKUP, spec(10_000, Duration.ofHours(24)));
        return manager;
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> spec(
            int maximumSize, Duration ttl) {
        return Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).build();
    }
}
