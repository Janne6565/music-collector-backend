package com.rekordo.services.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The one-time links that go out by mail: password resets and address confirmations.
 *
 * <p>Shared between the two so that they cannot drift apart. Both rely on the same property —
 * the raw token exists in the e-mail and nowhere else, and only its SHA-256 is stored, so a
 * database leak is not a pile of account takeovers. That is worth exactly one implementation.
 *
 * <p>The token is 256 bits from {@link SecureRandom}, URL-safe and unpadded so it survives
 * being pasted out of a mail client by hand.
 */
public final class OneTimeToken {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimeToken() {}

    public static String issue() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }
}
