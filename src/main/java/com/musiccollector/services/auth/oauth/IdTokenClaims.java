package com.musiccollector.services.auth.oauth;

import com.musiccollector.services.auth.oauth.OAuthService.ExternalIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads the identity out of an OIDC id token.
 *
 * <p>Deliberately does <em>not</em> verify the signature, and that is safe only because of
 * where it is used: the token came straight from the provider's own token endpoint over
 * TLS, in a response to a request this server made. It was never in the browser's hands.
 * An id token arriving by any other route must be verified against the provider's JWKS
 * before it is trusted.
 */
final class IdTokenClaims {

    private static final Logger log = LoggerFactory.getLogger(IdTokenClaims.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IdTokenClaims() {}

    static ExternalIdentity read(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            JsonNode claims = MAPPER.readTree(
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            JsonNode subject = claims.get("sub");
            if (subject == null) {
                return null;
            }
            JsonNode email = claims.get("email");
            JsonNode name = claims.get("name");
            return new ExternalIdentity(
                    subject.asString(),
                    email == null ? null : email.asString(),
                    name == null ? null : name.asString());
        } catch (RuntimeException e) {
            log.warn("Could not read the provider's id token", e);
            return null;
        }
    }
}
