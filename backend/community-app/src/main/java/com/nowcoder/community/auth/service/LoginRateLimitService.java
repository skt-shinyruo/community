package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class LoginRateLimitService {

    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final String KEY_PREFIX_IP = KEY_PREFIX + "ip:";
    private static final String KEY_PREFIX_USER = KEY_PREFIX + "user:";
    private static final String METRIC = "auth_login_rate_limit_total";

    private final LoginRateLimitProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ThreadPoolExecutor dependencyGuardExecutor = new ThreadPoolExecutor(
            0,
            4,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "login-rate-limit-guard");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public LoginRateLimitService(
            LoginRateLimitProperties properties,
            StringRedisTemplate redisTemplate,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    public void assertNotBlocked(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            runWithinDependencyBudget(() -> {
                int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
                int userLimit = Math.max(1, properties.getMaxFailuresPerUser());

                if (StringUtils.hasText(ip) && getCount(KEY_PREFIX_IP + ip.trim()) >= ipLimit) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
                }
                if (StringUtils.hasText(username)) {
                    String normalized = normalizeUsername(username);
                    if (getCount(KEY_PREFIX_USER + normalized) >= userLimit) {
                        record("blocked", ipSource);
                        throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
                    }
                }
                record("allowed", ipSource);
                return null;
            });
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            // 降级语义：限流依赖不可用时不阻断登录主链路（可用性优先），通过指标观测与告警兜底。
            record("degraded", ipSource);
        }
    }

    public void recordFailure(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            runWithinDependencyBudget(() -> {
                int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
                int userLimit = Math.max(1, properties.getMaxFailuresPerUser());

                if (StringUtils.hasText(ip)) {
                    int ipCount = increment(KEY_PREFIX_IP + ip.trim());
                    if (ipCount >= ipLimit) {
                        record("blocked", ipSource);
                        throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
                    }
                }
                if (StringUtils.hasText(username)) {
                    String normalized = normalizeUsername(username);
                    int userCount = increment(KEY_PREFIX_USER + normalized);
                    if (userCount >= userLimit) {
                        record("blocked", ipSource);
                        throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
                    }
                }
                record("allowed", ipSource);
                return null;
            });
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            record("degraded", ipSource);
        }
    }

    public void reset(String username, String ip) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            runWithinDependencyBudget(() -> {
                if (StringUtils.hasText(ip)) {
                    redisTemplate.delete(KEY_PREFIX_IP + ip.trim());
                }
                if (StringUtils.hasText(username)) {
                    redisTemplate.delete(KEY_PREFIX_USER + normalizeUsername(username));
                }
                return null;
            });
        } catch (RuntimeException ignored) {
        }
    }

    public boolean isCaptchaRequired(String username, String ip) {
        if (!properties.isEnabled()) {
            return false;
        }

        try {
            return runWithinDependencyBudget(() -> {
                int ipThreshold = properties.getCaptchaRequiredFailuresPerIp();
                int userThreshold = properties.getCaptchaRequiredFailuresPerUser();

                if (StringUtils.hasText(ip)) {
                    int count = getCount(KEY_PREFIX_IP + ip.trim());
                    if (ipThreshold <= 0 || count >= ipThreshold) {
                        return true;
                    }
                }
                if (StringUtils.hasText(username)) {
                    String normalized = normalizeUsername(username);
                    int count = getCount(KEY_PREFIX_USER + normalized);
                    if (userThreshold <= 0 || count >= userThreshold) {
                        return true;
                    }
                }
                return false;
            });
        } catch (RuntimeException e) {
            // 降级语义：依赖不可用时不强制验证码（避免把 Redis 抖动放大为“无法登录”）
            return false;
        }
    }

    @PreDestroy
    void shutdownDependencyGuardExecutor() {
        dependencyGuardExecutor.shutdownNow();
    }

    private void record(String outcome, String ipSource) {
        MeterRegistry meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        String source = StringUtils.hasText(ipSource) ? ipSource.trim().toLowerCase(Locale.ROOT) : "unknown";
        if (!"remote".equals(source) && !"xff".equals(source)) {
            source = "unknown";
        }
        String o = StringUtils.hasText(outcome) ? outcome.trim().toLowerCase(Locale.ROOT) : "unknown";
        meterRegistry.counter(METRIC, Tags.of("outcome", o, "ip_source", source)).increment();
    }

    private int getCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int increment(String key) {
        int windowSeconds = Math.max(1, properties.getWindowSeconds());
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return 0;
        }
        if (count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return count.intValue();
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private <T> T runWithinDependencyBudget(Callable<T> action) {
        int timeoutMs = Math.max(1, properties.getDependencyTimeoutMs());
        Future<T> future;
        try {
            future = dependencyGuardExecutor.submit(action);
        } catch (RejectedExecutionException e) {
            throw new RuntimeException("login rate limit dependency executor saturated", e);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("login rate limit dependency timed out", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("login rate limit dependency interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }
}
