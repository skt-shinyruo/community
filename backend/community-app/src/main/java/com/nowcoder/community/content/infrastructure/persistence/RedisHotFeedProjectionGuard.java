package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisHotFeedProjectionGuard implements HotFeedProjectionGuard {

    private static final String EVENT_KEY_PREFIX = "post:feed:hot:projection:event:";
    private static final String VERSION_KEY_PREFIX = "post:feed:hot:projection:version:";
    private static final String LOCK_KEY_PREFIX = "post:feed:hot:projection:lock:";
    private static final String EVENT_TTL_SECONDS = "604800";
    private static final String LOCK_TTL_MILLIS = "30000";
    private static final int LOCK_ATTEMPTS = 20;
    private static final long LOCK_BACKOFF_MILLIS = 25L;
    private static final DefaultRedisScript<Long> BEGIN_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return -1
            end
            local current = redis.call('GET', KEYS[3])
            local next = tonumber(ARGV[1])
            if next == nil or next <= 0 then
              return -1
            end
            if current ~= false then
              local currentNumber = tonumber(current)
              if currentNumber ~= nil and currentNumber > next then
                return -1
              end
            end
            local locked = redis.call('SET', KEYS[1], ARGV[2], 'NX', 'PX', ARGV[3])
            if locked then
              return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> CURRENT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            local current = redis.call('GET', KEYS[2])
            local next = tonumber(ARGV[2])
            if next == nil or next <= 0 then
              return 0
            end
            if current ~= false then
              local currentNumber = tonumber(current)
              if currentNumber ~= nil and currentNumber > next then
                return 0
              end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
            redis.call('SET', KEYS[3], ARGV[3])
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ABORT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisHotFeedProjectionGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ProjectionAttempt tryBegin(UUID postId, String sourceEventId, long sourceVersion) {
        if (postId == null || !StringUtils.hasText(sourceEventId) || sourceVersion <= 0L) {
            return ProjectionAttempt.rejected(postId, sourceEventId, sourceVersion);
        }
        String token = UUID.randomUUID().toString();
        String normalizedEventId = sourceEventId.trim();
        List<String> keys = List.of(lockKey(postId), eventKey(normalizedEventId), versionKey(postId));
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            Long started = redisTemplate.execute(
                    BEGIN_SCRIPT,
                    keys,
                    String.valueOf(sourceVersion),
                    token,
                    LOCK_TTL_MILLIS
            );
            if (Long.valueOf(1L).equals(started)) {
                return ProjectionAttempt.accepted(postId, normalizedEventId, sourceVersion, token);
            }
            if (Long.valueOf(-1L).equals(started)) {
                return ProjectionAttempt.rejected(postId, normalizedEventId, sourceVersion);
            }
            sleepBeforeRetry();
        }
        throw new IllegalStateException("hot feed projection lock busy: postId=" + postId);
    }

    @Override
    public boolean isCurrent(ProjectionAttempt attempt) {
        if (!accepted(attempt)) {
            return false;
        }
        Long current = redisTemplate.execute(
                CURRENT_SCRIPT,
                List.of(lockKey(attempt.postId()), versionKey(attempt.postId())),
                attempt.token(),
                String.valueOf(attempt.sourceVersion())
        );
        return Long.valueOf(1L).equals(current);
    }

    @Override
    public void commit(ProjectionAttempt attempt) {
        if (!accepted(attempt)) {
            return;
        }
        redisTemplate.execute(
                COMMIT_SCRIPT,
                List.of(lockKey(attempt.postId()), eventKey(attempt.sourceEventId()), versionKey(attempt.postId())),
                attempt.token(),
                EVENT_TTL_SECONDS,
                String.valueOf(attempt.sourceVersion())
        );
    }

    @Override
    public void abort(ProjectionAttempt attempt) {
        if (!accepted(attempt)) {
            return;
        }
        redisTemplate.execute(
                ABORT_SCRIPT,
                List.of(lockKey(attempt.postId())),
                attempt.token()
        );
    }

    private boolean accepted(ProjectionAttempt attempt) {
        return attempt != null
                && attempt.accepted()
                && attempt.postId() != null
                && StringUtils.hasText(attempt.sourceEventId())
                && StringUtils.hasText(attempt.token());
    }

    private String eventKey(String sourceEventId) {
        return EVENT_KEY_PREFIX + sourceEventId.trim();
    }

    private String versionKey(UUID postId) {
        return VERSION_KEY_PREFIX + postId;
    }

    private String lockKey(UUID postId) {
        return LOCK_KEY_PREFIX + postId;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(LOCK_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("hot feed projection lock wait interrupted", e);
        }
    }
}
