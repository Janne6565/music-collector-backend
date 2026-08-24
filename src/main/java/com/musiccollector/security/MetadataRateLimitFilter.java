package com.musiccollector.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.musiccollector.configuration.MusicBrainzProperties;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP quota in front of the open metadata proxy.
 *
 * <p>This is what makes an unauthenticated endpoint viable against an upstream capped at
 * one request per second: no login is required, but no single caller can monopolise the
 * shared budget either.
 *
 * <p>The client address is read from {@code X-Forwarded-For} when present. That header is
 * spoofable by anything that can reach the pod directly, so it is only trustworthy because
 * all external traffic arrives through Traefik, which overwrites it.
 */
@Component
public class MetadataRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MetadataRateLimitFilter.class);
    private static final String PROTECTED_PREFIX = "/api/v1/metadata";

    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder().maximumSize(50_000).expireAfterAccess(Duration.ofMinutes(10)).build();
    private final int requestsPerMinute;

    public MetadataRateLimitFilter(MusicBrainzProperties properties) {
        this.requestsPerMinute = properties.anonymousRequestsPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = clientAddress(request);
        Bucket bucket = buckets.get(client, unused -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Metadata rate limit hit by {}", client);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Too many metadata requests — slow down and retry shortly."}""");
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        int comma = forwarded.indexOf(',');
        return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }
}
