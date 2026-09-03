package com.jarvis.commerce.search;

import com.jarvis.commerce.product.Product;
import com.jarvis.commerce.product.ProductStatus;
import com.jarvis.commerce.product.Sku;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductSearchDocumentMapperTests {
    @Test
    void createsDenormalizedDocumentWithSkuPriceRange() {
        Product product = new Product("Java 后端课程", "Spring Boot 电商实战");
        product.putOnSale();
        Sku basic = new Sku(product, "BASIC", "基础版", new BigDecimal("99.00"));
        Sku pro = new Sku(product, "PRO", "专业版", new BigDecimal("199.00"));

        ProductSearchDocument document = new ProductSearchDocumentMapper()
                .map(10L, product, List.of(basic, pro));

        assertEquals("10", document.getId());
        assertEquals(ProductStatus.ON_SALE.name(), document.getStatus());
        assertEquals(List.of("基础版", "专业版"), document.getSkuNames());
        assertEquals(List.of("BASIC", "PRO"), document.getSkuCodes());
        assertEquals(new BigDecimal("99.00"), document.getMinPrice());
        assertEquals(new BigDecimal("199.00"), document.getMaxPrice());
    }
}
