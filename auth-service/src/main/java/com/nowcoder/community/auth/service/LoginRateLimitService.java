package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LoginRateLimitService {

    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final String KEY_PREFIX_IP = KEY_PREFIX + "ip:";
    private static final String KEY_PREFIX_USER = KEY_PREFIX + "user:";
    private static final String METRIC = "auth_login_rate_limit_total";

    private final LoginRateLimitProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

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
    }

    public void recordFailure(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }

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
    }

    public void reset(String username, String ip) {
        if (!properties.isEnabled()) {
            return;
        }
        if (StringUtils.hasText(ip)) {
            redisTemplate.delete(KEY_PREFIX_IP + ip.trim());
        }
        if (StringUtils.hasText(username)) {
            redisTemplate.delete(KEY_PREFIX_USER + normalizeUsername(username));
        }
    }

    public boolean isCaptchaRequired(String username, String ip) {
        if (!properties.isEnabled()) {
            return false;
        }

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
}
