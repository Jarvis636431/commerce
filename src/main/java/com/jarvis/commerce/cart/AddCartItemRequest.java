package com.jarvis.commerce.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

public record AddCartItemRequest(@NotNull @Positive Long skuId, @Positive @Max(99) int quantity) {
}
