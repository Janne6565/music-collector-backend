package com.musiccollector.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Discogs, which is where the physical pressings actually are.
 *
 * <p>MusicBrainz is a metadata database in which physical editions are secondary; Discogs
 * is a marketplace for records, so they are the whole point. The difference is not
 * marginal — Fred again..'s <em>ten days</em> has four vinyl pressings on Discogs and none
 * at all on MusicBrainz.
 *
 * <p>The token is optional but not really: without one Discogs caps us at 25 requests a
 * minute instead of 60, and returns empty strings for every cover image. Absent a token
 * this client still works, just slower and without art.
 */
@Validated
@ConfigurationProperties(prefix = "music-collector.discogs")
public record DiscogsProperties(
        @NotBlank String baseUrl,
        /** Discogs asks for a descriptive User-Agent, and rejects the default Java one outright. */
        @NotBlank String userAgent,
        /** A personal access token. Blank means anonymous: lower quota, no images. */
        String token,
        @Min(1) int requestsPerMinute) {

    public boolean authenticated() {
        return token != null && !token.isBlank();
    }
}
