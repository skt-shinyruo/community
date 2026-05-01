package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.PostScoreQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostScoreQueue implements PostScoreQueue {

    private static final DefaultRedisScript<Long> RETRY_ATTEMPT_SCRIPT = new DefaultRedisScript<>();

    private static final String LEGACY_SET_KEY = "post:score";
    private static final String QUEUE_ZSET_KEY = "post:score:z";
    private static final String RETRY_HASH_KEY = "post:score:retry";
    private static final Duration RETRY_HASH_TTL = Duration.ofDays(3);

    static {
        RETRY_ATTEMPT_SCRIPT.setResultType(Long.class);
        RETRY_ATTEMPT_SCRIPT.setScriptText(
                "local v = redis.call('hincrby', KEYS[1], ARGV[1], 1) " +
                        "if v == 1 then redis.call('pexpire', KEYS[1], ARGV[2]) end " +
                        "return v"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public RedisPostScoreQueue(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void add(UUID postId) {
        if (postId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(QUEUE_ZSET_KEY, postId.toString(), (double) now);
    }

    @Override
    public UUID pop() {
        // 1) v2 queue (ZSET): pop due items only (avoid tight retry loops)
        UUID v2 = popDueFromZset();
        if (v2 != null) {
            return v2;
        }

        // 2) best-effort legacy fallback (SET): avoid dropping old queued items after upgrade
        String legacy = redisTemplate.opsForSet().pop(LEGACY_SET_KEY);
        return parsePostIdOrNull(legacy);
    }

    @Override
    public void reenqueue(UUID postId) {
        if (postId == null) {
            return;
        }
        String member = postId.toString();
        Long attemptLong = redisTemplate.execute(
                RETRY_ATTEMPT_SCRIPT,
                List.of(RETRY_HASH_KEY),
                member,
                Long.toString(RETRY_HASH_TTL.toMillis())
        );
        long attempt = attemptLong == null ? 1L : Math.max(1L, attemptLong);

        long delayMs = computeBackoffMs(attempt);
        long dueAt = System.currentTimeMillis() + delayMs;
        redisTemplate.opsForZSet().add(QUEUE_ZSET_KEY, member, (double) dueAt);
    }

    @Override
    public void onSuccess(UUID postId) {
        if (postId == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().delete(RETRY_HASH_KEY, postId.toString());
        } catch (RuntimeException ignored) {
        }
    }

    private UUID popDueFromZset() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            Set<String> members = redisTemplate.opsForZSet().rangeByScore(QUEUE_ZSET_KEY, 0, now, 0, 1);
            if (members == null || members.isEmpty()) {
                return null;
            }
            String m = members.iterator().next();
            if (!StringUtils.hasText(m)) {
                // poison member: try removing and retry
                try {
                    redisTemplate.opsForZSet().remove(QUEUE_ZSET_KEY, m);
                } catch (RuntimeException ignored) {
                }
                continue;
            }
            Long removed = redisTemplate.opsForZSet().remove(QUEUE_ZSET_KEY, m);
            if (removed == null || removed <= 0) {
                // lost race: retry
                continue;
            }
            return parsePostIdOrNull(m);
        }
        return null;
    }

    private UUID parsePostIdOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long computeBackoffMs(long attempt) {
        // Goal: avoid tight loops within a single refresh batch. Backoff also helps protect dependencies on persistent failures.
        long baseMs = 5_000L;
        long maxMs = 10 * 60_000L;
        long exp = Math.max(0L, attempt - 1L);
        exp = Math.min(exp, 7L); // cap exponent to avoid overflow and excessive delays
        long delay = baseMs * (1L << (int) exp);
        delay = Math.min(delay, maxMs);
        long jitter = ThreadLocalRandom.current().nextLong(0L, 500L);
        return delay + jitter;
    }
}
