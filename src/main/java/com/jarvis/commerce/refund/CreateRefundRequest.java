package com.jarvis.commerce.refund;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CreateRefundRequest(
        @NotNull @Positive Long orderId,
        @NotBlank @Size(max = 500) String reason,
        @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount
) {}
