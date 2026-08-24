package com.musiccollector.model.core;

/** How a client wants its refresh token delivered. */
public enum TokenMode {
    /** Browsers: httpOnly cookie, unreadable by script. */
    COOKIE,
    /** Native apps: in the response body, for the platform keychain. */
    DIRECT;

    public static final String HEADER = "X-Token-Mode";

    public static TokenMode fromHeader(String header) {
        return "direct".equalsIgnoreCase(header) ? DIRECT : COOKIE;
    }
}
