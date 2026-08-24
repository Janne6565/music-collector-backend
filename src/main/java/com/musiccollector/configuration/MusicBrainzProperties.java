package com.musiccollector.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "music-collector.musicbrainz")
public record MusicBrainzProperties(
        @NotBlank String baseUrl,
        @NotBlank String coverArtBaseUrl,
        /**
         * MusicBrainz rejects callers without a descriptive, contactable User-Agent, and
         * bans ones that ignore the rate limit. Both are terms of use, not suggestions.
         */
        @NotBlank String userAgent,
        @Min(1) int requestsPerSecond,
        @Min(1) int anonymousRequestsPerMinute) {}
