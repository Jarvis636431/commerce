package com.jarvis.commerce.search;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchResponse(
        Long productId,
        String name,
        String description,
        List<String> skuNames,
        List<String> skuCodes,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
    static ProductSearchResponse from(ProductSearchDocument document) {
        return new ProductSearchResponse(Long.valueOf(document.getId()), document.getName(),
                document.getDescription(), document.getSkuNames(), document.getSkuCodes(),
                document.getMinPrice(), document.getMaxPrice());
    }
}
