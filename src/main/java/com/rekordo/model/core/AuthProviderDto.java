package com.rekordo.model.core;

/**
 * A sign-in provider the server can actually complete a flow with.
 *
 * The client renders a button per entry and nothing more, so an unconfigured provider is
 * simply absent rather than a button that fails when pressed.
 */
public record AuthProviderDto(String id, String displayName) {}
