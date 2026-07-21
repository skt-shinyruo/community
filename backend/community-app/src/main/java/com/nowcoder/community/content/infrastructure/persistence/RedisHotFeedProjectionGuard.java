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
    private static final String TOMBSTONE_KEY_PREFIX = "post:feed:hot:projection:tombstone:";
    private static final String EVENT_TTL_SECONDS = "604800";
    private static final String LOCK_TTL_MILLIS = "30000";
    private static final int LOCK_ATTEMPTS = 20;
    private static final long LOCK_BACKOFF_MILLIS = 25L;
    private static final DefaultRedisScript<Long> BEGIN_SCRIPT = new DefaultRedisScript<>("""
            local next = tonumber(ARGV[1])
            if next == nil or next <= 0 then
              return -1
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return -1
            end
            if redis.call('EXISTS', KEYS[4]) == 1 then
              return -1
            end
            if ARGV[2] ~= '1' then
              local current = redis.call('GET', KEYS[3])
              if current ~= false then
                local currentNumber = tonumber(current)
                if currentNumber == nil or currentNumber > next then
                  return -1
                end
              end
            end
            local locked = redis.call('SET', KEYS[1], ARGV[3], 'NX', 'PX', ARGV[4])
            if locked then
              return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> CURRENT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return -2
            end
            local next = tonumber(ARGV[2])
            if next == nil or next <= 0 then
              return 0
            end
            if redis.call('EXISTS', KEYS[3]) == 1 then
              return 0
            end
            if ARGV[3] ~= '1' then
              local current = redis.call('GET', KEYS[2])
              if current ~= false then
                local currentNumber = tonumber(current)
                if currentNumber == nil or currentNumber > next then
                  return 0
                end
              end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            local eventTtl = tonumber(ARGV[2])
            local next = tonumber(ARGV[3])
            if eventTtl == nil or eventTtl <= 0 or next == nil or next <= 0 then
              return 0
            end
            if ARGV[4] ~= '0' and ARGV[4] ~= '1' then
              return 0
            end
            local current = redis.call('GET', KEYS[3])
            local currentNumber = nil
            if current ~= false then
              currentNumber = tonumber(current)
              if currentNumber == nil then
                return 0
              end
            end
            redis.call('SET', KEYS[2], '1', 'EX', eventTtl)
            if currentNumber == nil or currentNumber < next then
              redis.call('SET', KEYS[3], ARGV[3])
            end
            if ARGV[4] == '1' then
              redis.call('SET', KEYS[4], '1')
            end
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
    public ProjectionAttempt tryBegin(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            boolean terminalDeletion
    ) {
        if (postId == null || !StringUtils.hasText(sourceEventId) || sourceVersion <= 0L) {
            return ProjectionAttempt.rejected(postId, sourceEventId, sourceVersion, terminalDeletion);
        }
        String token = UUID.randomUUID().toString();
        String normalizedEventId = sourceEventId.trim();
        List<String> keys = allKeys(postId, normalizedEventId);
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            Long started = redisTemplate.execute(
                    BEGIN_SCRIPT,
                    keys,
                    String.valueOf(sourceVersion),
                    deletionFlag(terminalDeletion),
                    token,
                    LOCK_TTL_MILLIS
            );
            if (Long.valueOf(1L).equals(started)) {
                return ProjectionAttempt.accepted(
                        postId,
                        normalizedEventId,
                        sourceVersion,
                        terminalDeletion,
                        token
                );
            }
            if (Long.valueOf(-1L).equals(started)) {
                return ProjectionAttempt.rejected(postId, normalizedEventId, sourceVersion, terminalDeletion);
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
                List.of(
                        lockKey(attempt.postId()),
                        versionKey(attempt.postId()),
                        tombstoneKey(attempt.postId())
                ),
                attempt.token(),
                String.valueOf(attempt.sourceVersion()),
                deletionFlag(attempt.terminalDeletion())
        );
        if (current == null || Long.valueOf(-2L).equals(current)) {
            throw new IllegalStateException(
                    "hot feed projection current check lost lease: postId=" + attempt.postId()
                            + ", sourceEventId=" + attempt.sourceEventId());
        }
        return Long.valueOf(1L).equals(current);
    }

    @Override
    public void commit(ProjectionAttempt attempt) {
        if (!accepted(attempt)) {
            return;
        }
        Long committed = redisTemplate.execute(
                COMMIT_SCRIPT,
                allKeys(attempt.postId(), attempt.sourceEventId()),
                attempt.token(),
                EVENT_TTL_SECONDS,
                String.valueOf(attempt.sourceVersion()),
                deletionFlag(attempt.terminalDeletion())
        );
        if (!Long.valueOf(1L).equals(committed)) {
            throw new IllegalStateException(
                    "hot feed projection commit lost lease: postId=" + attempt.postId()
                            + ", sourceEventId=" + attempt.sourceEventId());
        }
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

    private List<String> allKeys(UUID postId, String sourceEventId) {
        return List.of(
                lockKey(postId),
                eventKey(postId, sourceEventId),
                versionKey(postId),
                tombstoneKey(postId)
        );
    }

    private String eventKey(UUID postId, String sourceEventId) {
        return EVENT_KEY_PREFIX + hashTag(postId) + ":" + sourceEventId.trim();
    }

    private String versionKey(UUID postId) {
        return VERSION_KEY_PREFIX + hashTag(postId);
    }

    private String lockKey(UUID postId) {
        return LOCK_KEY_PREFIX + hashTag(postId);
    }

    private String tombstoneKey(UUID postId) {
        return TOMBSTONE_KEY_PREFIX + hashTag(postId);
    }

    private String hashTag(UUID postId) {
        return "{" + postId + "}";
    }

    private String deletionFlag(boolean terminalDeletion) {
        return terminalDeletion ? "1" : "0";
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
