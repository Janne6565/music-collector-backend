package com.musiccollector.model.action;

import jakarta.validation.constraints.Size;

/**
 * A change to the account's own details. Only the display name so far -- the e-mail is the
 * sign-in identity and the handle has its own endpoint, because it carries a rate limit and
 * a reservation the display name does not.
 *
 * @param displayName what the app should call you, or null/blank to go back to no name at
 *     all. Clearing is a real choice, so a blank one is stored as null rather than refused.
 */
public record UpdateProfileRequest(@Size(max = 120) String displayName) {}
