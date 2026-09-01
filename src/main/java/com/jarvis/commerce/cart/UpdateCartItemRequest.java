package com.jarvis.commerce.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(@Positive @Max(99) int quantity) {
}
