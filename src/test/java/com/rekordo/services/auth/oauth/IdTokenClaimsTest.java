package com.rekordo.services.auth.oauth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading the identity out of an id token, including the claim that decides who it may be. */
class IdTokenClaimsTest {

    private static String idToken(String claimsJson) {
        String body = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
        return "header." + body + ".signature";
    }

    @Test
    void readsABooleanEmailVerified() {
        assertThat(IdTokenClaims.read(idToken("{\"sub\":\"1\",\"email\":\"a@b.c\",\"email_verified\":true}"))
                        .emailVerified())
                .isTrue();
    }

    @Test
    void readsAppleStringShapedEmailVerified() {
        // Apple sends the claim as the string "true". Read as a boolean it is false, which
        // would refuse every Apple sign-in against an address that already has an account.
        assertThat(IdTokenClaims.read(idToken("{\"sub\":\"1\",\"email\":\"a@b.c\",\"email_verified\":\"true\"}"))
                        .emailVerified())
                .isTrue();
    }

    @Test
    void treatsAMissingClaimAsUnverified() {
        // A provider that will not say has not said yes.
        assertThat(IdTokenClaims.read(idToken("{\"sub\":\"1\",\"email\":\"a@b.c\"}")).emailVerified())
                .isFalse();
    }

    @Test
    void treatsAnExplicitFalseAsUnverified() {
        assertThat(IdTokenClaims.read(idToken("{\"sub\":\"1\",\"email\":\"a@b.c\",\"email_verified\":false}"))
                        .emailVerified())
                .isFalse();
    }
}
