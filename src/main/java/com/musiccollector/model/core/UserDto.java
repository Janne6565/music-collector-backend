package com.musiccollector.model.core;

import java.time.Instant;
import java.util.UUID;

/**
 * @param displayName what the app calls you, or null if the account predates the field.
 *                    Clients fall back to the e-mail rather than inventing one.
 */
public record UserDto(UUID id, String email, String displayName, Instant createdAt) {}
