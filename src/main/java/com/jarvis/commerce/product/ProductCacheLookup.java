package com.jarvis.commerce.product;

public record ProductCacheLookup(boolean hit, ProductResponse value) {
    public static ProductCacheLookup miss() { return new ProductCacheLookup(false, null); }
    public static ProductCacheLookup found(ProductResponse value) { return new ProductCacheLookup(true, value); }
    public static ProductCacheLookup notFound() { return new ProductCacheLookup(true, null); }
}
