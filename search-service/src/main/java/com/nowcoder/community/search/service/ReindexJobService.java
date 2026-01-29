package com.nowcoder.community.search.service;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.SimpleErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * reindex single-flight + jobId（SSOT）：
 * - 防止重复触发导致 ES/下游被压垮；
 * - 便于审计：并发请求返回 409，并带上当前 jobId（便于定位“是谁在跑”）。
 */
@Service
public class ReindexJobService {

    private static final String LOCK_KEY = "search:reindex:lock";
    private static final String JOB_KEY = "search:reindex:job";

    private final StringRedisTemplate redisTemplate;

    public ReindexJobService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public ReindexJob tryStart() {
        if (redisTemplate == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "reindex 存储不可用");
        }

        String jobId = newJobId();
        boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, jobId, Duration.ofMinutes(30)));
        if (acquired) {
            redisTemplate.opsForValue().set(JOB_KEY, jobId, Duration.ofHours(2));
            return new ReindexJob(jobId, true);
        }

        String existing = redisTemplate.opsForValue().get(LOCK_KEY);
        if (!StringUtils.hasText(existing)) {
            existing = redisTemplate.opsForValue().get(JOB_KEY);
        }
        return new ReindexJob(StringUtils.hasText(existing) ? existing.trim() : null, false);
    }

    public void finish(String jobId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String current = redisTemplate.opsForValue().get(LOCK_KEY);
            if (!StringUtils.hasText(current) || !StringUtils.hasText(jobId)) {
                return;
            }
            if (current.trim().equals(jobId.trim())) {
                redisTemplate.delete(LOCK_KEY);
            }
        } catch (Exception ignored) {
        }
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

    public record ReindexJob(String jobId, boolean acquired) {
    }
}

