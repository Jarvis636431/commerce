package com.jarvis.commerce.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SkuResponse(
        Long id,
        Long productId,
        String code,
        String name,
        BigDecimal price,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static SkuResponse from(Sku sku) {
        return new SkuResponse(sku.getId(), sku.getProduct().getId(), sku.getCode(), sku.getName(),
                sku.getPrice(), sku.getCreatedAt(), sku.getUpdatedAt());
    }
}
