package com.jarvis.commerce.product;

public record ProductMainImage(Long id, String url) {
    static ProductMainImage from(ProductImage image) {
        return new ProductMainImage(image.getId(), "/api/products/" + image.getProductId()
                + "/images/" + image.getId() + "/content");
    }
}
