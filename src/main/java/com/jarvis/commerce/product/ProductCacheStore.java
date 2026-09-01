package com.jarvis.commerce.product;

public interface ProductCacheStore {
    ProductCacheLookup get(long productId);
    void put(ProductResponse product);
    void putNotFound(long productId);
    void evict(long productId);
}
