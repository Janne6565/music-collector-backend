package com.musiccollector.model.action;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Claiming a handle for the first time, or changing one. The same request for both: the
 * service knows which it is from whether the account already has one.
 */
public record ClaimHandleRequest(@NotBlank @Size(min = 3, max = 30) String handle) {}
