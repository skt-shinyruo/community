package com.nowcoder.community.analytics.repo;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class RedisAnalyticsUserOrdinalRepository implements AnalyticsUserOrdinalRepository {

    private static final String USER_ORDINAL_MAP_KEY = "{analytics:user-ordinal}:map";
    private static final String USER_ORDINAL_SEQ_KEY = "{analytics:user-ordinal}:seq";
    private static final DefaultRedisScript<Long> RESOLVE_ORDINAL_SCRIPT = new DefaultRedisScript<>();

    static {
        RESOLVE_ORDINAL_SCRIPT.setResultType(Long.class);
        RESOLVE_ORDINAL_SCRIPT.setScriptText(
                "local existing = redis.call('hget', KEYS[1], ARGV[1]) " +
                        "if existing then return tonumber(existing) end " +
                        "local next = redis.call('incr', KEYS[2]) " +
                        "redis.call('hset', KEYS[1], ARGV[1], next) " +
                        "return next"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public RedisAnalyticsUserOrdinalRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int resolveOrdinal(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        Long value = redisTemplate.execute(
                RESOLVE_ORDINAL_SCRIPT,
                List.of(USER_ORDINAL_MAP_KEY, USER_ORDINAL_SEQ_KEY),
                userId.toString()
        );
        if (value == null || value <= 0 || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("invalid analytics user ordinal: " + value);
        }
        return value.intValue();
    }
}
