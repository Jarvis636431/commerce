package com.jarvis.commerce.refund;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundFailureNotification(
        @NotBlank @Size(max = 100) String notificationId,
        @NotBlank @Size(max = 500) String reason
) {}
