package com.musiccollector.services.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppleUserPayloadTest {

    @Test
    void readsTheNameAppleSendsOnTheFirstAuthorization() {
        String payload = "{\"name\":{\"firstName\":\"Jonas\",\"lastName\":\"Meyer\"},\"email\":\"j@example.com\"}";

        assertThat(AppleUserPayload.displayName(payload)).isEqualTo("Jonas Meyer");
    }

    @Test
    void treatsAMissingNameAsOrdinary() {
        // Apple sends the user object once and never again, so every later sign-in — and
        // every sign-in by someone who deleted their account here — arrives without one.
        assertThat(AppleUserPayload.displayName(null)).isNull();
        assertThat(AppleUserPayload.displayName("")).isNull();
        assertThat(AppleUserPayload.displayName("{\"email\":\"j@example.com\"}")).isNull();
        assertThat(AppleUserPayload.displayName("{\"name\":{}}")).isNull();
    }

    @Test
    void copesWithHalfANameRatherThanEmittingAStraySpace() {
        assertThat(AppleUserPayload.displayName("{\"name\":{\"firstName\":\"Jonas\"}}")).isEqualTo("Jonas");
        assertThat(AppleUserPayload.displayName("{\"name\":{\"lastName\":\"Meyer\"}}")).isEqualTo("Meyer");
    }

    @Test
    void refusesToFailASignInOverAMalformedName() {
        // The field passes through the browser, unlike the id token. It is cosmetic, so
        // anything unreadable must degrade to "no name", never to a failed sign-in.
        assertThat(AppleUserPayload.displayName("not json at all")).isNull();
    }

    @Test
    void capsAnAbsurdlyLongName() {
        String payload = "{\"name\":{\"firstName\":\"" + "a".repeat(500) + "\"}}";

        assertThat(AppleUserPayload.displayName(payload)).hasSize(100);
    }
}
