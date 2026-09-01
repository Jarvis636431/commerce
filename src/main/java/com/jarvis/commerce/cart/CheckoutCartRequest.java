package com.jarvis.commerce.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CheckoutCartRequest(@NotNull @Positive Long addressId) {
}
