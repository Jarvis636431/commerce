package com.jarvis.commerce.inventory;

import java.time.OffsetDateTime;

public record InventoryResponse(
        Long id,
        Long skuId,
        int availableQuantity,
        int reservedQuantity,
        long version,
        OffsetDateTime updatedAt
) {
    static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getSku().getId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getVersion(),
                inventory.getUpdatedAt()
        );
    }
}
