package com.jarvis.commerce.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductSearchResponse(
        Long productId,
        String name,
        String description,
        List<String> skuNames,
        List<String> skuCodes,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String mainImageUrl,
        float score,
        Map<String, List<String>> highlights
) {
    static ProductSearchResponse from(ProductSearchDocument document) {
        return new ProductSearchResponse(Long.valueOf(document.getId()), document.getName(),
                document.getDescription(), document.getSkuNames(), document.getSkuCodes(),
                document.getMinPrice(), document.getMaxPrice(), document.getMainImageUrl(), 0, Map.of());
    }

    static ProductSearchResponse from(org.springframework.data.elasticsearch.core.SearchHit<ProductSearchDocument> hit) {
        ProductSearchDocument document = hit.getContent();
        return new ProductSearchResponse(Long.valueOf(document.getId()), document.getName(),
                document.getDescription(), document.getSkuNames(), document.getSkuCodes(),
                document.getMinPrice(), document.getMaxPrice(), document.getMainImageUrl(), hit.getScore(), hit.getHighlightFields());
    }
}
