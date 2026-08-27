package com.rekordo.model.action;

import jakarta.validation.constraints.NotBlank;

/**
 * A device saying where it can be reached.
 *
 * @param deviceId  the client's own stable id for itself, so signing in twice on one phone
 *                  updates a row rather than growing the list
 * @param pushToken an Expo push token; the server never sees an Apple or Google one
 */
public record RegisterDeviceRequest(
        @NotBlank String deviceId, @NotBlank String pushToken, @NotBlank String platform, String label) {}
