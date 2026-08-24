package com.musiccollector.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "music-collector.jwt")
public record JwtProperties(
        /**
         * HMAC signing key. Deliberately has no default anywhere: the service must fail to
         * start rather than run on a value that is in a public repository.
         */
        @NotBlank @Size(min = 32, message = "The JWT secret must be at least 32 characters")
        String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        /** Used when "keep me signed in" is off: long enough for one sitting, no more. */
        @NotNull Duration sessionRefreshTokenTtl) {}
