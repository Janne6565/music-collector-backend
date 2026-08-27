package com.rekordo.model.action;

import jakarta.validation.constraints.NotBlank;

/** The undo token out of the notice sent to the old address. */
public record CancelEmailChangeRequest(@NotBlank String token) {}
