package com.jarvis.commerce.search;

import com.jarvis.commerce.product.Product;
import com.jarvis.commerce.product.Sku;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
public class ProductSearchDocumentMapper {
    public ProductSearchDocument map(long productId, Product product, List<Sku> skus) {
        BigDecimal minPrice = skus.stream().map(Sku::getPrice).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal maxPrice = skus.stream().map(Sku::getPrice).max(Comparator.naturalOrder()).orElse(null);
        return new ProductSearchDocument(Long.toString(productId), product.getName(), product.getDescription(),
                product.getStatus().name(), skus.stream().map(Sku::getName).toList(),
                skus.stream().map(Sku::getCode).toList(), minPrice, maxPrice, product.getUpdatedAt());
    }
}
