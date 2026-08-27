package com.rekordo.model.core;

/**
 * One row of the grid on 22a.
 *
 * @param mailLocked drawn as a lock where the switch would be. It is sent rather than
 *                   inferred client-side so that the two apps cannot disagree about which
 *                   category is un-silenceable.
 */
public record NotificationPreferenceDto(
        NotificationCategory category, boolean mail, boolean push, boolean mailLocked) {}
