package com.musiccollector.model.core;

/**
 * What a successful sign-in returns.
 *
 * For browsers the refresh token is absent here and set as an httpOnly cookie instead, so
 * an XSS bug cannot read a durable credential. Native clients have no cookie jar worth
 * relying on, so they ask for {@code X-Token-Mode: direct} and get it in the body to store
 * in the platform keychain.
 */
public record SessionDto(String accessToken, String refreshToken, UserDto user) {}
