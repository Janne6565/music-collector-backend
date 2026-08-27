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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP quota on handle search.
 *
 * <p>Handle search is open to signed-out visitors, which makes it the one endpoint that
 * could be walked to enumerate everybody on the platform. The three-character minimum and
 * the twenty-result cap make that slow; this makes it boring. It is not a defence against a
 * determined scraper and is not meant to be — it is what keeps a casual one from getting
 * the whole directory in an afternoon.
 *
 * <p>Generous enough for autocomplete: a burst covers typing a handle out letter by letter
 * several times over.
 */
@Component
public class ProfileSearchRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ProfileSearchRateLimitFilter.class);

    private static final String PROTECTED_PATH = "/api/v1/profiles";
    private static final int SEARCHES_PER_MINUTE = 60;

    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder().maximumSize(50_000).expireAfterAccess(Duration.ofMinutes(10)).build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the search itself. Reading one profile by handle is a page view, and rate
        // limiting those would throttle a shelf's own images along with it.
        return !PROTECTED_PATH.equals(request.getRequestURI())
                || !HttpMethod.GET.matches(request.getMethod());
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

        log.warn("Profile search rate limit hit by {}", client);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Too many searches — slow down and retry shortly."}""");
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(SEARCHES_PER_MINUTE)
                        .refillGreedy(SEARCHES_PER_MINUTE, Duration.ofMinutes(1)))
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
