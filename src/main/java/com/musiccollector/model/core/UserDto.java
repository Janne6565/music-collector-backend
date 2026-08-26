package com.musiccollector.model.core;

import java.time.Instant;
import java.util.UUID;

/**
 * @param displayName   what the app calls you, or null if the account predates the field.
 *                      Clients fall back to the e-mail rather than inventing one.
 * @param emailVerified whether the address has been confirmed. A flag rather than the
 *                      timestamp behind it: the client only ever asks yes or no, and the
 *                      date the address was proved is nobody's business but the server's.
 * @param hasPassword   whether there is a password to ask for. An account made through a
 *                      provider has none, and the change-address screen has to know that
 *                      rather than showing a field nothing could ever be typed into.
 */
public record UserDto(
        UUID id, String email, String displayName, Instant createdAt, boolean emailVerified, boolean hasPassword) {}
