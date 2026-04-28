package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.auth.domain.model.LoginRateLimitKey;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.auth.domain.service.LoginRateLimitDomainService;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

@Service
public class LoginRateLimitApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitApplicationService.class);
    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final String KEY_PREFIX_IP = KEY_PREFIX + "ip:";
    private static final String KEY_PREFIX_USER = KEY_PREFIX + "user:";
    private static final String METRIC = "auth_login_rate_limit_total";

    private final LoginRateLimitProperties properties;
    private final LoginRateLimitRepository loginRateLimitRepository;
    private final LoginRateLimitDomainService loginRateLimitDomainService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public LoginRateLimitApplicationService(
            LoginRateLimitProperties properties,
            LoginRateLimitRepository loginRateLimitRepository,
            LoginRateLimitDomainService loginRateLimitDomainService,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.properties = properties;
        this.loginRateLimitRepository = loginRateLimitRepository;
        this.loginRateLimitDomainService = loginRateLimitDomainService;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    public void assertNotBlocked(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int userLimit = Math.max(1, properties.getMaxFailuresPerUser());
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);

            if (StringUtils.hasText(key.ip()) && getCount(ipKey(key)) >= ipLimit) {
                record("blocked", ipSource);
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
            }
            if (StringUtils.hasText(key.username())) {
                if (getCount(userKey(key)) >= userLimit) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
                }
            }
            record("allowed", ipSource);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            record("dependency_error", ipSource);
            log.warn("[auth][login-rate-limit] assertNotBlocked failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "登录风控暂时不可用，请稍后重试");
        }
    }

    public void recordFailure(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int userLimit = Math.max(1, properties.getMaxFailuresPerUser());
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);

            if (StringUtils.hasText(key.ip())) {
                int ipCount = increment(ipKey(key));
                if (ipCount >= ipLimit) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
                }
            }
            if (StringUtils.hasText(key.username())) {
                int userCount = increment(userKey(key));
                if (userCount >= userLimit) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
                }
            }
            record("allowed", ipSource);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            record("dependency_error", ipSource);
            log.warn("[auth][login-rate-limit] recordFailure failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "登录风控暂时不可用，请稍后重试");
        }
    }

    public void reset(String username, String ip) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);
            if (StringUtils.hasText(key.ip())) {
                loginRateLimitRepository.delete(ipKey(key));
            }
            if (StringUtils.hasText(key.username())) {
                loginRateLimitRepository.delete(userKey(key));
            }
        } catch (RuntimeException e) {
            record("dependency_error", null);
            log.warn("[auth][login-rate-limit] reset failed: {}", e.toString());
        }
    }

    public boolean isCaptchaRequired(String username, String ip) {
        if (!properties.isEnabled()) {
            return false;
        }

        try {
            int ipThreshold = properties.getCaptchaRequiredFailuresPerIp();
            int userThreshold = properties.getCaptchaRequiredFailuresPerUser();
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);

            if (StringUtils.hasText(key.ip())) {
                int count = getCount(ipKey(key));
                if (ipThreshold <= 0 || count >= ipThreshold) {
                    return true;
                }
            }
            if (StringUtils.hasText(key.username())) {
                int count = getCount(userKey(key));
                if (userThreshold <= 0 || count >= userThreshold) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            record("dependency_error", null);
            log.warn("[auth][login-rate-limit] isCaptchaRequired failed: {}", e.toString());
            return true;
        }
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
        return loginRateLimitRepository.count(key);
    }

    private int increment(String key) {
        int windowSeconds = Math.max(1, properties.getWindowSeconds());
        return loginRateLimitRepository.increment(key, windowSeconds);
    }

    private String ipKey(LoginRateLimitKey key) {
        return KEY_PREFIX_IP + key.ip();
    }

    private String userKey(LoginRateLimitKey key) {
        return KEY_PREFIX_USER + key.username();
    }
}
