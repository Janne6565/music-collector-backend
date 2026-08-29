package com.rekordo.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rekordo.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        /**
         * Refused above this size, before a single byte reaches object storage. Applies to
         * the profile picture, which arrives at full resolution because the server is the
         * one that renders it down.
         */
        @Min(1) long maxUploadBytes,
        /**
         * The same guard for a sleeve photo, and a much lower number: both clients scale a
         * chosen picture to {@code 1600px} before it is offered, so anything approaching a
         * megabyte here is already unusual and anything past this did not come from them.
         */
        @Min(1) long maxPhotoBytes,
        /**
         * What one account may keep in object storage in total -- every sleeve photo it has
         * not deleted, plus its profile picture. Cover art is not in it: that is fetched
         * from MusicBrainz and Discogs at display time and never stored.
         */
        @Min(1) long quotaBytes) {}
