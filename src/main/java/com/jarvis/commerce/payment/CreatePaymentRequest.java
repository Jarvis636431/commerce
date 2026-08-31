package com.jarvis.commerce.payment;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(@NotNull Long orderId) {
}
