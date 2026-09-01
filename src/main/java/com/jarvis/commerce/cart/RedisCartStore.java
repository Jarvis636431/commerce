package com.jarvis.commerce.cart;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("!test")
public class RedisCartStore implements CartStore {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            local next = current + tonumber(ARGV[2])
            if next > tonumber(ARGV[3]) then return -1 end
            redis.call('HSET', KEYS[1], ARGV[1], next)
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return next
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisCartStore(StringRedisTemplate redisTemplate,
                          @Value("${commerce.cart.ttl:P30D}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public Map<Long, Integer> getItems(long userId) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(userId));
            Map<Long, Integer> result = new LinkedHashMap<>();
            entries.forEach((skuId, quantity) -> result.put(
                    Long.parseLong(skuId.toString()), Integer.parseInt(quantity.toString())));
            if (!entries.isEmpty()) redisTemplate.expire(key(userId), ttl);
            return result;
        } catch (RedisConnectionFailureException exception) {
            throw new CartUnavailableException("Shopping cart is temporarily unavailable", exception);
        }
    }

    @Override
    public int increment(long userId, long skuId, int quantity, int maximumQuantity) {
        try {
            Long result = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key(userId)),
                    Long.toString(skuId), Integer.toString(quantity), Integer.toString(maximumQuantity),
                    Long.toString(ttl.toSeconds()));
            return result == null ? -1 : result.intValue();
        } catch (RedisConnectionFailureException exception) {
            throw new CartUnavailableException("Shopping cart is temporarily unavailable", exception);
        }
    }

    @Override
    public void put(long userId, long skuId, int quantity) {
        try {
            redisTemplate.opsForHash().put(key(userId), Long.toString(skuId), Integer.toString(quantity));
            redisTemplate.expire(key(userId), ttl);
        } catch (RedisConnectionFailureException exception) {
            throw new CartUnavailableException("Shopping cart is temporarily unavailable", exception);
        }
    }

    @Override
    public void remove(long userId, long skuId) {
        try {
            redisTemplate.opsForHash().delete(key(userId), Long.toString(skuId));
        } catch (RedisConnectionFailureException exception) {
            throw new CartUnavailableException("Shopping cart is temporarily unavailable", exception);
        }
    }

    @Override
    public void clear(long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (RedisConnectionFailureException exception) {
            throw new CartUnavailableException("Shopping cart is temporarily unavailable", exception);
        }
    }

    private String key(long userId) { return "cart:" + userId; }
}
