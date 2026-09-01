package com.jarvis.commerce.cart;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryCartStore implements CartStore {

    private final Map<Long, Map<Long, Integer>> carts = new ConcurrentHashMap<>();

    @Override
    public Map<Long, Integer> getItems(long userId) {
        return new LinkedHashMap<>(carts.getOrDefault(userId, Map.of()));
    }

    @Override
    public int increment(long userId, long skuId, int quantity, int maximumQuantity) {
        Map<Long, Integer> cart = carts.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        synchronized (cart) {
            int next = Math.addExact(cart.getOrDefault(skuId, 0), quantity);
            if (next > maximumQuantity) return -1;
            cart.put(skuId, next);
            return next;
        }
    }

    @Override
    public void put(long userId, long skuId, int quantity) {
        carts.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(skuId, quantity);
    }

    @Override
    public void remove(long userId, long skuId) {
        Map<Long, Integer> cart = carts.get(userId);
        if (cart != null) cart.remove(skuId);
    }

    @Override
    public void removeUnchangedItems(long userId, Map<Long, Integer> expectedItems) {
        Map<Long, Integer> cart = carts.get(userId);
        if (cart == null) return;
        synchronized (cart) {
            expectedItems.forEach((skuId, quantity) -> cart.remove(skuId, quantity));
            if (cart.isEmpty()) carts.remove(userId, cart);
        }
    }

    @Override
    public void clear(long userId) { carts.remove(userId); }
}
