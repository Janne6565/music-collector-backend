package com.musiccollector.services.auth.oauth;

import com.musiccollector.configuration.OAuthProperties;
import io.jsonwebtoken.Jwts;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Apple does not issue a static client secret. It expects a short-lived JWT signed with an
 * ES256 key you download from the developer portal as a .p8 file, and it has to be
 * regenerated before it expires — so it is built per request rather than configured.
 *
 * <p>This is the one provider-specific wrinkle; everything else about Apple is ordinary
 * OIDC. Untested against Apple's live endpoints, because that needs an Apple Developer
 * account to issue the key.
 */
public final class AppleClientSecret {

    /** Apple caps this at six months; minutes is plenty for a single token exchange. */
    private static final Duration LIFETIME = Duration.ofMinutes(5);

    private AppleClientSecret() {}

    public static String create(OAuthProperties.Provider provider) {
        return Jwts.builder()
                .header()
                .keyId(provider.keyId())
                .and()
                .issuer(provider.teamId())
                .subject(provider.clientId())
                .audience()
                .add("https://appleid.apple.com")
                .and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(LIFETIME)))
                .signWith(privateKey(provider.clientSecret()))
                .compact();
    }

    private static PrivateKey privateKey(String pkcs8Pem) {
        String base64 = pkcs8Pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalStateException("Apple sign-in key is not a readable PKCS#8 EC key", e);
        }
    }
}
