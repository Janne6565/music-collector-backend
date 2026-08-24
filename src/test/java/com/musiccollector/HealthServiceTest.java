package com.musiccollector;

import com.musiccollector.model.core.HealthDto;
import com.musiccollector.services.HealthService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HealthServiceTest {

    @Test
    void reportsTheInjectedBuildVersion() {
        Instant before = Instant.now();

        HealthDto health = new HealthService("main-abc1234").current();

        assertThat(health).satisfies(h -> {
            assertThat(h.status()).isEqualTo("ok");
            assertThat(h.version()).isEqualTo("main-abc1234");
            assertThat(h.time()).isAfterOrEqualTo(before);
        });
    }
}
