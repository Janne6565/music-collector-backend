package com.rekordo.model.action;

import jakarta.validation.constraints.NotBlank;

/** The one-time token out of a confirmation link. */
public record ConfirmEmailRequest(@NotBlank String token) {}
