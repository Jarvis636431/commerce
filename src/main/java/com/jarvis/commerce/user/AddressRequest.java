package com.jarvis.commerce.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 100) String receiverName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{6,20}$", message = "must contain 6 to 20 digits and may start with +") String phone,
        @NotBlank @Size(max = 100) String province,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String district,
        @NotBlank @Size(max = 500) String detailAddress,
        @Size(max = 20) String postalCode,
        boolean defaultAddress) {
}
