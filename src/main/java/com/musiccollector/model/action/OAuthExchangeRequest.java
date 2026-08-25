package com.musiccollector.model.action;

import jakarta.validation.constraints.NotBlank;

/** The one-time code a native app received on its deep link after an external sign-in. */
public record OAuthExchangeRequest(@NotBlank String code) {}
