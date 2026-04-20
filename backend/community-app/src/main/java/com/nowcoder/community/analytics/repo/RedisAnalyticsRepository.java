package com.nowcoder.community.analytics.repo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "analytics.storage", havingValue = "redis", matchIfMissing = true)
public class RedisAnalyticsRepository implements AnalyticsRepository {

    private static final String PREFIX_UV = "uv:";
    private static final String PREFIX_DAU = "dau:";
    private static final String PREFIX_UV_TMP = "uv:tmp:";
    private static final String PREFIX_DAU_TMP = "dau:tmp:";
    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;
    // unionKey 属于“临时计算中间结果”，即使 finally 清理失败/进程崩溃，也应尽快过期避免 Redis 膨胀。
    private static final Duration UNION_KEY_TTL = Duration.ofSeconds(60);

    private static final DefaultRedisScript<Long> UV_UNION_AND_EXPIRE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> DAU_UNION_AND_EXPIRE_SCRIPT = new DefaultRedisScript<>();

    static {
        UV_UNION_AND_EXPIRE_SCRIPT.setResultType(Long.class);
        UV_UNION_AND_EXPIRE_SCRIPT.setScriptText(
                "redis.call('pfmerge', KEYS[1], unpack(KEYS, 2, #KEYS)) " +
                        "redis.call('pexpire', KEYS[1], ARGV[1]) " +
                        "return 1"
        );

        DAU_UNION_AND_EXPIRE_SCRIPT.setResultType(Long.class);
        DAU_UNION_AND_EXPIRE_SCRIPT.setScriptText(
                "redis.call('bitop', 'OR', KEYS[1], unpack(KEYS, 2, #KEYS)) " +
                        "redis.call('pexpire', KEYS[1], ARGV[1]) " +
                        "return 1"
        );
    }

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
        String unionKey = tempUnionKey(PREFIX_UV_TMP, start, end);
        try {
            List<String> unionKeys = new ArrayList<>(keys.size() + 1);
            unionKeys.add(unionKey);
            unionKeys.addAll(keys);
            redisTemplate.execute(
                    UV_UNION_AND_EXPIRE_SCRIPT,
                    unionKeys,
                    Long.toString(UNION_KEY_TTL.toMillis())
            );
            Long size = redisTemplate.opsForHyperLogLog().size(unionKey);
            return size == null ? 0 : size;
        } finally {
            safeDelete(unionKey);
        }
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
        String unionKey = tempUnionKey(PREFIX_DAU_TMP, start, end);
        byte[] unionKeyBytes = unionKey.getBytes(StandardCharsets.UTF_8);
        try {
            List<String> unionKeys = new ArrayList<>(keys.size() + 1);
            unionKeys.add(unionKey);
            unionKeys.addAll(keys);
            redisTemplate.execute(
                    DAU_UNION_AND_EXPIRE_SCRIPT,
                    unionKeys,
                    Long.toString(UNION_KEY_TTL.toMillis())
            );
            Long count = redisTemplate.execute((RedisCallback<Long>) connection -> connection.stringCommands().bitCount(unionKeyBytes));
            return count == null ? 0 : count;
        } finally {
            safeDelete(unionKey);
        }
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

    private String tempUnionKey(String prefix, LocalDate start, LocalDate end) {
        String s = start == null ? "null" : DF.format(start);
        String e = end == null ? "null" : DF.format(end);
        String rand = UUID.randomUUID().toString().replace("-", "");
        return prefix + s + ":" + e + ":" + rand;
    }

    private void safeDelete(String key) {
        if (redisTemplate == null || key == null || key.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ignored) {
        }
    }
}
