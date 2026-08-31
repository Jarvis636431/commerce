package com.jarvis.commerce.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentSuccessNotification(
        @NotBlank @Size(max = 100) String notificationId,
        @NotBlank @Size(max = 100) String externalTransactionNo,
        @NotNull @DecimalMin("0.00") BigDecimal amount
) {
}
