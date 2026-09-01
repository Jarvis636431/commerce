package com.jarvis.commerce.product;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("!test")
public class RedisProductCacheStore implements ProductCacheStore {

    private static final Logger log = LoggerFactory.getLogger(RedisProductCacheStore.class);
    private static final String NOT_FOUND = "__NOT_FOUND__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Duration notFoundTtl;
    private final Duration ttlJitter;

    public RedisProductCacheStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                  @Value("${commerce.product-cache.ttl:PT10M}") Duration ttl,
                                  @Value("${commerce.product-cache.not-found-ttl:PT30S}") Duration notFoundTtl,
                                  @Value("${commerce.product-cache.ttl-jitter:PT2M}") Duration ttlJitter) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.notFoundTtl = notFoundTtl;
        this.ttlJitter = ttlJitter;
    }

    @Override
    public ProductCacheLookup get(long productId) {
        try {
            String value = redisTemplate.opsForValue().get(key(productId));
            if (value == null) return ProductCacheLookup.miss();
            if (NOT_FOUND.equals(value)) return ProductCacheLookup.notFound();
            return ProductCacheLookup.found(objectMapper.readValue(value, ProductResponse.class));
        } catch (RuntimeException exception) {
            log.warn("Product {} cache read failed; falling back to database", productId, exception);
            return ProductCacheLookup.miss();
        }
    }

    @Override
    public void put(ProductResponse product) {
        try {
            redisTemplate.opsForValue().set(key(product.id()), objectMapper.writeValueAsString(product), randomizedTtl());
        } catch (RuntimeException exception) {
            log.warn("Product {} cache write failed", product.id(), exception);
        }
    }

    @Override
    public void putNotFound(long productId) {
        try {
            redisTemplate.opsForValue().set(key(productId), NOT_FOUND, notFoundTtl);
        } catch (RuntimeException exception) {
            log.warn("Product {} null-cache write failed", productId, exception);
        }
    }

    @Override
    public void evict(long productId) {
        try {
            redisTemplate.delete(key(productId));
        } catch (RuntimeException exception) {
            log.warn("Product {} cache eviction failed", productId, exception);
        }
    }

    private Duration randomizedTtl() {
        long jitterMillis = ttlJitter.toMillis();
        if (jitterMillis <= 0) return ttl;
        return ttl.plusMillis(ThreadLocalRandom.current().nextLong(jitterMillis + 1));
    }

    private String key(long productId) { return "product:detail:" + productId; }
}
