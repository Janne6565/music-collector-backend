package com.musiccollector.model.action;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // Length only. Composition rules push people towards predictable substitutions
        // without adding real strength.
        @NotBlank @Size(min = 10, max = 200, message = "Use at least 10 characters") String password) {}
