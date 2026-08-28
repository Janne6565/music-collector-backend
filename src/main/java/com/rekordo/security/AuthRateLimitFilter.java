package com.rekordo.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP quota on sign-in and sign-up.
 *
 * Password endpoints are the one place where an attacker gets unlimited free guesses, so
 * they are limited far more tightly than the metadata proxy. Refresh is excluded: an active
 * app refreshes on a timer, and throttling that would sign people out.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    /** Sends mail to an address the caller chose, so it needs the same restraint. */
    private static final String FORGOT = "/api/v1/auth/forgot-password";
    /** Open, and a token to guess. */
    private static final String CONFIRM = "/api/v1/auth/confirm-email";
    /** Sends mail. Signed in, so the quota is a brake on a stuck client, not on an attacker. */
    private static final String RESEND = "/api/v1/auth/confirm-email/resend";
    /** Open, and another token to guess. */
    private static final String CANCEL_CHANGE = "/api/v1/auth/email-change/cancel";
    /** Open, and sends mail to an address the caller chose. */
    private static final String REQUEST_CONFIRMATION = "/api/v1/auth/confirm-email/request";

    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder().maximumSize(50_000).expireAfterAccess(Duration.ofMinutes(30)).build();
    private final int attemptsPerHour;

    public AuthRateLimitFilter(@Value("${rekordo.auth.attempts-per-hour:20}") int attemptsPerHour) {
        this.attemptsPerHour = attemptsPerHour;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(REGISTER.equals(path)
                || LOGIN.equals(path)
                || FORGOT.equals(path)
                || CONFIRM.equals(path)
                || RESEND.equals(path)
                || CANCEL_CHANGE.equals(path)
                || REQUEST_CONFIRMATION.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = clientAddress(request);
        Bucket bucket = buckets.get(client, unused -> Bucket.builder()
                .addLimit(limit -> limit.capacity(attemptsPerHour).refillGreedy(attemptsPerHour, Duration.ofHours(1)))
                .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Auth rate limit hit by {} on {}", client, request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Too many sign-in attempts. Wait a while before trying again."}""");
    }

    /**
     * Read from X-Forwarded-For when present. That header is spoofable by anything that can
     * reach the pod directly, so it is only trustworthy because all external traffic arrives
     * through Traefik, which overwrites it.
     */
    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        int comma = forwarded.indexOf(',');
        return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }
}
