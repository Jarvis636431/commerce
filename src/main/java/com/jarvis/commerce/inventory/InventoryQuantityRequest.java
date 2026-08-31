package com.jarvis.commerce.inventory;

import jakarta.validation.constraints.Min;

public record InventoryQuantityRequest(
        @Min(1) int quantity
) {
}
