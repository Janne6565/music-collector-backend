package com.musiccollector.model.action;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param password the account's current password, asked for because a stray session should
 *                 not be able to walk off with the account. Blank is accepted only by an
 *                 account that has no password at all — one made through a provider — where
 *                 there is nothing to ask for.
 */
public record ChangeEmailRequest(@NotBlank @Email String email, String password) {}
