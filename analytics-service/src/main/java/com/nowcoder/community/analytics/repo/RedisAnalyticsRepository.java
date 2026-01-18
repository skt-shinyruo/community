package com.nowcoder.community.analytics.repo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "analytics.storage", havingValue = "redis", matchIfMissing = true)
public class RedisAnalyticsRepository implements AnalyticsRepository {

    private static final String PREFIX_UV = "uv:";
    private static final String PREFIX_DAU = "dau:";
    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StringRedisTemplate redisTemplate;

    public RedisAnalyticsRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void recordUv(LocalDate date, String ip) {
        redisTemplate.opsForHyperLogLog().add(PREFIX_UV + DF.format(date), ip);
    }

    @Override
    public long calculateUv(LocalDate start, LocalDate end) {
        List<String> keys = rangeKeys(PREFIX_UV, start, end);
        if (keys.isEmpty()) {
            return 0;
        }
        String unionKey = PREFIX_UV + DF.format(start) + ":" + DF.format(end);
        redisTemplate.opsForHyperLogLog().union(unionKey, keys.toArray(new String[0]));
        Long size = redisTemplate.opsForHyperLogLog().size(unionKey);
        return size == null ? 0 : size;
    }

    @Override
    public void recordDau(LocalDate date, int userId) {
        redisTemplate.opsForValue().setBit(PREFIX_DAU + DF.format(date), userId, true);
    }

    @Override
    public long calculateDau(LocalDate start, LocalDate end) {
        List<String> keys = rangeKeys(PREFIX_DAU, start, end);
        if (keys.isEmpty()) {
            return 0;
        }
        String unionKey = PREFIX_DAU + DF.format(start) + ":" + DF.format(end);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.bitOp(org.springframework.data.redis.connection.RedisStringCommands.BitOperation.OR,
                    unionKey.getBytes(StandardCharsets.UTF_8),
                    keys.stream().map(k -> k.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new));
            return null;
        });
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> connection.stringCommands().bitCount(unionKey.getBytes(StandardCharsets.UTF_8)));
        return count == null ? 0 : count;
    }

    private List<String> rangeKeys(String prefix, LocalDate start, LocalDate end) {
        List<String> keys = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            keys.add(prefix + DF.format(d));
            d = d.plusDays(1);
        }
        return keys;
    }
}
