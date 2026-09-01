package com.jarvis.commerce.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String account, @NotBlank String password) {
}
