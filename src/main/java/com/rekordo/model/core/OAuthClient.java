package com.rekordo.model.core;

/**
 * Which kind of client began an external sign-in.
 *
 * <p>The two differ only at the very end of the flow, but they differ completely there: a
 * browser is handed a refresh cookie and sent back to the web app, while a native app is
 * sent back to its own URL scheme with a one-time code it exchanges for a session, because
 * it has no cookie jar and keeps its tokens in the keychain instead.
 */
public enum OAuthClient {
    WEB,
    MOBILE;

    public static OAuthClient fromParam(String value) {
        return "mobile".equalsIgnoreCase(value) ? MOBILE : WEB;
    }
}
