package com.rekordo.services.metrics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The split is a string prefix and nothing else, so it is worth pinning: get it wrong and
 * the panel is not empty, it is confidently wrong in a way nobody would notice.
 */
class StorageMetricsTest {

    @Test
    void avatarKeysAreAvatars() {
        assertThat(StorageMetrics.kindOf("avatars/" + UUID.randomUUID())).isEqualTo(StorageMetrics.AVATAR);
    }

    @Test
    void photoKeysAreUserSlashPhoto() {
        String key = "%s/%s".formatted(UUID.randomUUID(), UUID.randomUUID());
        assertThat(StorageMetrics.kindOf(key)).isEqualTo(StorageMetrics.PHOTO);
    }

    @Test
    void anythingUnrecognisedCountsAsAPhoto() {
        // A key from some future feature lands in the bigger bucket rather than in neither,
        // so the totals still add up to what is actually being paid for.
        assertThat(StorageMetrics.kindOf("exports/2026-08.zip")).isEqualTo(StorageMetrics.PHOTO);
    }
}
