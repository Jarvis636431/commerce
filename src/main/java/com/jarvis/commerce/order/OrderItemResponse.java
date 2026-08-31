package com.jarvis.commerce.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long skuId,
        String skuCode,
        String skuName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal
) {
    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getSkuId(), item.getSkuCode(), item.getSkuName(),
                item.getUnitPrice(), item.getQuantity(), item.getSubtotal());
    }
}
