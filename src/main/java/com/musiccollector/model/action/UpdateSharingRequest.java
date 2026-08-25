package com.musiccollector.model.action;

import com.musiccollector.model.core.Visibility;
import jakarta.validation.constraints.NotNull;

/**
 * The whole Sharing screen saved at once. Every field is required: a partial update would
 * make "leave this one alone" and "set this one to its default" indistinguishable, and on a
 * privacy screen that ambiguity is the wrong one to have.
 */
public record UpdateSharingRequest(
        @NotNull Visibility collectionVisibility,
        @NotNull Visibility wishlistVisibility,
        @NotNull Boolean pricesPublic,
        @NotNull Boolean findable) {}
