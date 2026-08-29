package com.rekordo.model.core;

import java.time.Instant;

/**
 * The picture that now stands on the account, as the Account row needs it back (27d,
 * state 4).
 *
 * @param url       where the bytes are, cache-busted. The same string the profile DTOs
 *                  carry, so the row can show the new picture without another round trip.
 * @param updatedAt when it landed. "Updated just now" is drawn from this rather than from
 *                  the client's own clock, which may disagree.
 */
public record AvatarDto(String url, Instant updatedAt) {}
