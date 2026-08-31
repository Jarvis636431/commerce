package com.jarvis.commerce.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        String orderNo,
        OrderStatus status,
        BigDecimal totalAmount,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OrderAddressResponse shippingAddress,
        List<OrderItemResponse> items
) {
    static OrderResponse from(CustomerOrder order, List<OrderItem> items) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getOrderNo(), order.getStatus(), order.getTotalAmount(),
                order.getVersion(), order.getCreatedAt(), order.getUpdatedAt(),
                OrderAddressResponse.from(order),
                items.stream().map(OrderItemResponse::from).toList());
    }
}
