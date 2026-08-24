package com.musiccollector.model.core;

/**
 * What a successful sign-in returns.
 *
 * The refresh token is not in here — it is set as an httpOnly cookie the browser cannot
 * read, so an XSS bug cannot walk off with a long-lived credential. The access token is
 * short-lived and kept in memory by the client.
 */
public record SessionDto(String accessToken, UserDto user) {}
