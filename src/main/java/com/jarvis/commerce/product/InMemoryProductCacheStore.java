package com.jarvis.commerce.product;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryProductCacheStore implements ProductCacheStore {

    private final Map<Long, ProductCacheLookup> cache = new ConcurrentHashMap<>();

    @Override
    public ProductCacheLookup get(long productId) {
        return cache.getOrDefault(productId, ProductCacheLookup.miss());
    }

    @Override
    public void put(ProductResponse product) { cache.put(product.id(), ProductCacheLookup.found(product)); }

    @Override
    public void putNotFound(long productId) { cache.put(productId, ProductCacheLookup.notFound()); }

    @Override
    public void evict(long productId) { cache.remove(productId); }
}
