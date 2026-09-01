package com.jarvis.commerce.cart;

import com.jarvis.commerce.product.ProductStatus;

import java.math.BigDecimal;

public record CartItemResponse(Long skuId, Long productId, String skuCode, String skuName,
                               BigDecimal unitPrice, int quantity, BigDecimal subtotal,
                               ProductStatus productStatus, Integer availableQuantity,
                               boolean purchasable) {
}
