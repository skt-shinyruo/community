package com.nowcoder.community.search.service;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.SimpleErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * reindex single-flight + jobId（SSOT）：
 * - 防止重复触发导致 ES/下游被压垮；
 * - 便于审计：并发请求返回 409，并带上当前 jobId（便于定位“是谁在跑”）。
 */
@Service
public class ReindexJobService {

    private static final Logger log = LoggerFactory.getLogger(ReindexJobService.class);

    private static final String LOCK_KEY = "search:reindex:lock";
    private static final String JOB_KEY = "search:reindex:job";

    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_JOB_TTL = Duration.ofHours(2);
    private static final Duration DEFAULT_RENEW_INTERVAL = Duration.ofSeconds(60);

    private static final DefaultRedisScript<Long> RENEW_IF_OWNER_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER_SCRIPT = new DefaultRedisScript<>();

    static {
        RENEW_IF_OWNER_SCRIPT.setResultType(Long.class);
        RENEW_IF_OWNER_SCRIPT.setScriptText(
                // KEYS[1]=lockKey, ARGV[1]=jobId, ARGV[2]=ttlSeconds
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('expire', KEYS[1], tonumber(ARGV[2])) " +
                        "else return 0 end"
        );
        RELEASE_IF_OWNER_SCRIPT.setResultType(Long.class);
        RELEASE_IF_OWNER_SCRIPT.setScriptText(
                // KEYS[1]=lockKey, ARGV[1]=jobId
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
    }

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService renewScheduler;
    private final Duration lockTtl;
    private final Duration jobTtl;
    private final Duration renewInterval;

    @Autowired
    public ReindexJobService(StringRedisTemplate redisTemplate) {
        this(redisTemplate,
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "search-reindex-lock-renew");
                    t.setDaemon(true);
                    return t;
                }),
                DEFAULT_LOCK_TTL,
                DEFAULT_JOB_TTL,
                DEFAULT_RENEW_INTERVAL
        );
    }

    ReindexJobService(
            StringRedisTemplate redisTemplate,
            ScheduledExecutorService renewScheduler,
            Duration lockTtl,
            Duration jobTtl,
            Duration renewInterval
    ) {
        this.redisTemplate = redisTemplate;
        this.renewScheduler = renewScheduler;
        this.lockTtl = lockTtl == null ? DEFAULT_LOCK_TTL : lockTtl;
        this.jobTtl = jobTtl == null ? DEFAULT_JOB_TTL : jobTtl;
        this.renewInterval = renewInterval == null ? DEFAULT_RENEW_INTERVAL : renewInterval;
    }

    public ReindexJob tryStart() {
        if (redisTemplate == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "reindex 存储不可用");
        }

        String jobId = newJobId();
        boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, jobId, lockTtl));
        if (acquired) {
            redisTemplate.opsForValue().set(JOB_KEY, jobId, jobTtl);
            return new ReindexJob(jobId, true);
        }

        String existing = redisTemplate.opsForValue().get(LOCK_KEY);
        if (!StringUtils.hasText(existing)) {
            existing = redisTemplate.opsForValue().get(JOB_KEY);
        }
        return new ReindexJob(StringUtils.hasText(existing) ? existing.trim() : null, false);
    }

    public AutoCloseable startRenewal(String jobId) {
        if (redisTemplate == null || renewScheduler == null || !StringUtils.hasText(jobId)) {
            return () -> {
            };
        }

        long ttlSeconds = Math.max(10L, (lockTtl == null ? DEFAULT_LOCK_TTL : lockTtl).getSeconds());
        long intervalSeconds = Math.max(1L, (renewInterval == null ? DEFAULT_RENEW_INTERVAL : renewInterval).getSeconds());

        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> ref = new AtomicReference<>();

        Runnable task = () -> {
            if (closed.get()) {
                return;
            }
            try {
                boolean ok = renewIfOwner(jobId.trim(), ttlSeconds);
                if (!ok) {
                    // owner 丢失：停止续租，避免误续租扩大影响面
                    ScheduledFuture<?> f = ref.get();
                    if (f != null) {
                        f.cancel(false);
                    }
                }
            } catch (Exception ex) {
                // 续租失败不直接终止任务：TTL 作为兜底；同时保留日志便于排查。
                log.warn("[reindex] lock renew failed (jobId={}): {}", jobId, ex.toString());
            }
        };

        ScheduledFuture<?> future = renewScheduler.scheduleAtFixedRate(task, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        ref.set(future);

        return () -> {
            closed.set(true);
            ScheduledFuture<?> f = ref.get();
            if (f != null) {
                f.cancel(false);
            }
        };
    }

    public void finish(String jobId) {
        if (redisTemplate == null) {
            return;
        }
        releaseIfOwner(jobId);
    }

    public void conflict(String jobId) {
        String suffix = StringUtils.hasText(jobId) ? (" (jobId=" + jobId.trim() + ")") : "";
        throw new BusinessException(new SimpleErrorCode(409, "reindex 任务正在执行" + suffix, 409));
    }

    private String newJobId() {
        try {
            return UUID.randomUUID().toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private boolean renewIfOwner(String jobId, long ttlSeconds) {
        if (redisTemplate == null || !StringUtils.hasText(jobId)) {
            return false;
        }
        Long r = redisTemplate.execute(
                RENEW_IF_OWNER_SCRIPT,
                Collections.singletonList(LOCK_KEY),
                jobId,
                Long.toString(ttlSeconds)
        );
        return r != null && r > 0;
    }

    private void releaseIfOwner(String jobId) {
        if (redisTemplate == null || !StringUtils.hasText(jobId)) {
            return;
        }
        try {
            redisTemplate.execute(
                    RELEASE_IF_OWNER_SCRIPT,
                    Collections.singletonList(LOCK_KEY),
                    jobId.trim()
            );
        } catch (Exception ignored) {
        }
    }

    public record ReindexJob(String jobId, boolean acquired) {
    }
}
