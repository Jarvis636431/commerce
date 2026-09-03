package com.jarvis.commerce.product;

import java.time.OffsetDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        ProductStatus status,
        ProductMainImage mainImage,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                null,
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    static ProductResponse from(Product product, ProductMainImage mainImage) {
        ProductResponse base = from(product);
        return new ProductResponse(base.id(), base.name(), base.description(), base.status(), mainImage,
                base.version(), base.createdAt(), base.updatedAt());
    }
}
