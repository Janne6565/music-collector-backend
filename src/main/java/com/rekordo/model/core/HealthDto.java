package com.rekordo.model.core;

import java.time.Instant;

/**
 * @param status  always "ok" — reaching this endpoint at all is the liveness signal
 * @param version the release this artifact was built as, or "dev" outside the pipeline
 * @param builtAt when the artifact was built; null for a build with no build-info
 * @param time    now, so a stale cached response is obvious
 */
public record HealthDto(String status, String version, Instant builtAt, Instant time) {}
