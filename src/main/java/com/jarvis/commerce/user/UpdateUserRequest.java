package com.jarvis.commerce.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "must contain 6 to 20 digits and may start with +") String phone) {
}
