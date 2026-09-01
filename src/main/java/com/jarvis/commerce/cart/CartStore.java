package com.jarvis.commerce.cart;

import java.util.Map;

public interface CartStore {
    Map<Long, Integer> getItems(long userId);
    int increment(long userId, long skuId, int quantity, int maximumQuantity);
    void put(long userId, long skuId, int quantity);
    void remove(long userId, long skuId);
    void clear(long userId);
}
