package com.jarvis.commerce.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Long userId, int itemCount, int totalQuantity,
                           BigDecimal totalAmount, List<CartItemResponse> items) {
}
