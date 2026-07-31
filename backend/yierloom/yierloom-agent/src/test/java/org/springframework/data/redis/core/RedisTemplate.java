package org.springframework.data.redis.core;

public class RedisTemplate {
    public Object execute(String key, String value) {
        return "redis-ok";
    }
}
