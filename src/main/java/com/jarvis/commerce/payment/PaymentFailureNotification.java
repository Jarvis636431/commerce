package com.jarvis.commerce.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentFailureNotification(
        @NotBlank @Size(max = 100) String notificationId,
        @NotBlank @Size(max = 500) String reason
) {
}
