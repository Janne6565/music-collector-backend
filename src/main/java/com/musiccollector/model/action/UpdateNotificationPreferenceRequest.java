package com.musiccollector.model.action;

import com.musiccollector.model.core.NotificationCategory;
import jakarta.validation.constraints.NotNull;

/** One switch, flipped. The screen saves as you go, so a request is never the whole grid. */
public record UpdateNotificationPreferenceRequest(
        @NotNull NotificationCategory category, @NotNull Boolean mail, @NotNull Boolean push) {}
