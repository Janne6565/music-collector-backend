package com.rekordo.model.action;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** An address to send a fresh confirmation link to, from a browser with no session. */
public record RequestEmailConfirmationRequest(@NotBlank @Email String email) {}
