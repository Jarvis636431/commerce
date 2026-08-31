package com.jarvis.commerce.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        BigDecimal totalAmount,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderItemResponse> items
) {
    static OrderResponse from(CustomerOrder order, List<OrderItem> items) {
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getStatus(), order.getTotalAmount(),
                order.getVersion(), order.getCreatedAt(), order.getUpdatedAt(),
                items.stream().map(OrderItemResponse::from).toList());
    }
}
