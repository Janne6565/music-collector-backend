package com.musiccollector.model.core;

import java.util.List;

/**
 * The whole grid, plus the one fact that decides whether its second column can do anything.
 *
 * @param pushAvailable whether any device on this account could receive a push at all.
 *                      False today for every account: there is no push transport yet, so the
 *                      column says so plainly rather than showing switches that would
 *                      silently do nothing (22a). The stored choices are kept either way —
 *                      when push arrives nobody has to set them a second time.
 */
public record NotificationPreferencesDto(List<NotificationPreferenceDto> categories, boolean pushAvailable) {}
