package com.nowcoder.community.infra.scheduler;

// 分布式 single-flight 保护：用于定时任务/批处理在多实例部署下避免重复执行。
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 分布式 single-flight 任务保护器（基于 Redis）：
 * - 适用于“可跳过/可重试”的定时任务（cleanup / reconcile 等）
 * - 使用 setIfAbsent + TTL 获取锁；释放使用 compare-and-del（避免误删他人锁）
 *
 * <p>注意：当 Redis 不可用时，调用方应选择 fail-closed（直接跳过）或 fail-open（继续执行），
 * 具体由任务风险决定。</p>
 */
public class SingleFlightTaskGuard {

    private static final Logger log = LoggerFactory.getLogger(SingleFlightTaskGuard.class);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );

        REFRESH_SCRIPT.setResultType(Long.class);
        REFRESH_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('pexpire', KEYS[1], ARGV[2]) " +
                        "else return 0 end"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public SingleFlightTaskGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Lock tryAcquire(String name, Duration ttl) {
        if (!StringUtils.hasText(name) || ttl == null) {
            return null;
        }
        if (redisTemplate == null) {
            return null;
        }

        String key = "sf:task:" + name.trim();
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            if (Boolean.TRUE.equals(ok)) {
                return new Lock(key, token);
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("[single-flight] acquire failed: key={}, err={}", key, e.toString());
            return null;
        }
    }

    public void release(Lock lock) {
        if (lock == null || !StringUtils.hasText(lock.key()) || !StringUtils.hasText(lock.token())) {
            return;
        }
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lock.key()), lock.token());
        } catch (RuntimeException e) {
            log.warn("[single-flight] release failed: key={}, err={}", lock.key(), e.toString());
        }
    }

    public boolean refresh(Lock lock, Duration ttl) {
        if (lock == null || !StringUtils.hasText(lock.key()) || !StringUtils.hasText(lock.token())) {
            return false;
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        if (redisTemplate == null) {
            return false;
        }
        try {
            Long refreshed = redisTemplate.execute(
                    REFRESH_SCRIPT,
                    Collections.singletonList(lock.key()),
                    lock.token(),
                    Long.toString(ttl.toMillis())
            );
            return refreshed != null && refreshed > 0;
        } catch (RuntimeException e) {
            log.warn("[single-flight] refresh failed: key={}, err={}", lock.key(), e.toString());
            return false;
        }
    }

    public record Lock(String key, String token) {
    }
}
