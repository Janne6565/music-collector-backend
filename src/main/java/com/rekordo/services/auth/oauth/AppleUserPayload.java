package com.rekordo.services.auth.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the display name out of Apple's {@code user} form field.
 *
 * <p>Apple's id token carries no name claim, so this field is the only place the name ever
 * appears — and Apple sends it on the <em>first</em> authorization of an account and never
 * again. A user who signs in, is deleted here, and signs in again comes back nameless, and
 * the only way to make Apple resend it is to revoke the app under the Apple ID's security
 * settings. So treat a missing name as ordinary, never as an error.
 *
 * <p>The field is untrusted: it arrives through the browser, unlike the id token, which
 * came from Apple's token endpoint directly. It is therefore used only for the cosmetic
 * display name — never to identify the account, which is keyed on the id token's subject.
 */
final class AppleUserPayload {

    private static final Logger log = LoggerFactory.getLogger(AppleUserPayload.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_NAME_LENGTH = 100;

    private AppleUserPayload() {}

    /** @return the display name Apple sent, or null when it sent none */
    static String displayName(String userJson) {
        if (userJson == null || userJson.isBlank()) {
            return null;
        }
        try {
            JsonNode name = MAPPER.readTree(userJson).get("name");
            if (name == null) {
                return null;
            }
            String full = (text(name.get("firstName")) + " " + text(name.get("lastName"))).trim();
            if (full.isBlank()) {
                return null;
            }
            return full.length() > MAX_NAME_LENGTH ? full.substring(0, MAX_NAME_LENGTH) : full;
        } catch (RuntimeException e) {
            // A name is a nicety; failing the whole sign-in over it would be absurd.
            log.warn("Could not read Apple's user payload", e);
            return null;
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asString().trim();
    }
}
