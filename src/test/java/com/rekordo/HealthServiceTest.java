package com.rekordo;

import com.rekordo.model.core.HealthDto;
import com.rekordo.services.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<BuildProperties> provider(BuildProperties value) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static BuildProperties buildInfo(String release, Instant time) {
        Properties properties = new Properties();
        if (release != null) {
            properties.setProperty("release", release);
        }
        if (time != null) {
            properties.setProperty("time", String.valueOf(time.toEpochMilli()));
        }
        return new BuildProperties(properties);
    }

    @Test
    void reportsTheReleaseBakedIntoTheArtifact() {
        Instant built = Instant.parse("2026-08-24T16:00:00Z");

        HealthDto health = new HealthService(provider(buildInfo("0.1.0", built))).current();

        assertThat(health).satisfies(h -> {
            assertThat(h.status()).isEqualTo("ok");
            assertThat(h.version()).isEqualTo("0.1.0");
            assertThat(h.builtAt()).isEqualTo(built);
        });
    }

    @Test
    void reportsDevWhenThereIsNoBuildInfo() {
        // Running from an IDE or a test: there is no artifact, so there is no version to
        // claim. Saying "dev" is honest; inventing one would not be.
        HealthDto health = new HealthService(provider(null)).current();

        assertThat(health.version()).isEqualTo("dev");
        assertThat(health.builtAt()).isNull();
    }

    @Test
    void reportsDevWhenBuildInfoCarriesNoRelease() {
        HealthDto health = new HealthService(provider(buildInfo(null, Instant.now()))).current();

        assertThat(health.version()).isEqualTo("dev");
    }

    @Test
    void stampsTheCurrentTimeSoAStaleCachedResponseIsObvious() {
        Instant before = Instant.now();

        HealthDto health = new HealthService(provider(buildInfo("0.1.0", null))).current();

        assertThat(health.time()).isAfterOrEqualTo(before);
    }
}
