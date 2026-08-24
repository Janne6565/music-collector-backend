package com.musiccollector.model.core;

/**
 * Carried as a claim so a refresh token can never be presented as an access token. Without
 * this, a long-lived refresh token would authenticate every request.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
