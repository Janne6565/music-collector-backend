package com.rekordo.model.action;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 10, max = 200, message = "Use at least 10 characters") String password) {}
