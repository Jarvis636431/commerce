package com.jarvis.commerce.inventory;

import jakarta.validation.constraints.Min;

public record InitializeInventoryRequest(
        @Min(0) int quantity
) {
}
