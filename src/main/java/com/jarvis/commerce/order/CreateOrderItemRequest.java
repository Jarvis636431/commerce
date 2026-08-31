package com.jarvis.commerce.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderItemRequest(
        @NotNull Long skuId,
        @Min(1) int quantity
) {
}
