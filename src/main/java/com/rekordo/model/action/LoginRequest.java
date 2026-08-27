package com.rekordo.model.action;

import jakarta.validation.constraints.NotBlank;

/**
 * @param rememberMe when false the refresh token is short-lived and its cookie is a session
 *                   cookie, so closing the browser ends the session. Defaults to true for
 *                   clients that do not send it, which is the behaviour that existed before
 *                   the flag did.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password, Boolean rememberMe) {

    public boolean remember() {
        return rememberMe == null || rememberMe;
    }
}
