package com.jarvis.commerce.refund;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
        @NotNull @Positive Long orderId,
        @NotBlank @Size(max = 500) String reason
) {}
