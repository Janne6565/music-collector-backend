package com.rekordo.services;

import com.rekordo.model.core.HealthDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HealthService {

    /** What a build reports when it was not produced by the image pipeline. */
    private static final String UNRELEASED = "dev";

    private final String release;
    private final Instant builtAt;

    /**
     * Reads the version out of the jar's own build-info rather than an environment
     * variable. Baked into the artifact at build time, it cannot drift from the image it
     * describes — a redeploy that forgot to update a config map would previously have left
     * the service confidently misreporting its own version.
     *
     * <p>{@link BuildProperties} is absent when running from an IDE or a test, so it is
     * injected as a provider rather than required.
     */
    public HealthService(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.release = build == null ? UNRELEASED : build.get("release");
        this.builtAt = build == null ? null : build.getTime();
    }

    public HealthDto current() {
        return new HealthDto("ok", release == null ? UNRELEASED : release, builtAt, Instant.now());
    }
}
