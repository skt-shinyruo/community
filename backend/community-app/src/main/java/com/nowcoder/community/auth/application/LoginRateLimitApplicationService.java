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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LoginRateLimitApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitApplicationService.class);
    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final String KEY_PREFIX_IP = KEY_PREFIX + "ip:";
    private static final String KEY_PREFIX_INPUT = KEY_PREFIX + "input:";
    private static final String KEY_PREFIX_SUBJECT = KEY_PREFIX + "subject:";
    private static final String IN_FLIGHT_KEY_PREFIX = "auth:login:inflight:";
    private static final String METRIC = "auth_login_rate_limit_total";
    private final LoginRateLimitProperties properties;
    private final LoginRateLimitRepository loginRateLimitRepository;
    private final LoginRateLimitDomainService loginRateLimitDomainService;
    private final PasswordResetTokenDeriver identifierDeriver;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ScheduledExecutorService leaseRenewer;

    public LoginRateLimitApplicationService(
            LoginRateLimitProperties properties,
            LoginRateLimitRepository loginRateLimitRepository,
            LoginRateLimitDomainService loginRateLimitDomainService,
            PasswordResetTokenDeriver identifierDeriver,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Qualifier("loginRateLimitLeaseRenewer") ScheduledExecutorService leaseRenewer
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.loginRateLimitRepository = Objects.requireNonNull(
                loginRateLimitRepository, "loginRateLimitRepository must not be null");
        this.loginRateLimitDomainService = Objects.requireNonNull(
                loginRateLimitDomainService, "loginRateLimitDomainService must not be null");
        this.identifierDeriver = Objects.requireNonNull(identifierDeriver, "identifierDeriver must not be null");
        this.meterRegistryProvider = Objects.requireNonNull(
                meterRegistryProvider, "meterRegistryProvider must not be null");
        this.leaseRenewer = Objects.requireNonNull(leaseRenewer, "leaseRenewer must not be null");
    }

    public void recordFailure(String subject, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int subjectLimit = Math.max(1, properties.getMaxFailuresPerUser());
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(subject, ip);

            int ipCount = StringUtils.hasText(key.ip()) ? increment(ipKey(key)) : 0;
            int subjectCount = StringUtils.hasText(key.subject())
                    ? increment(subjectKey(key.subject())) : 0;
            if (StringUtils.hasText(key.ip()) && ipCount >= ipLimit) {
                record("blocked", ipSource);
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
            }
            if (StringUtils.hasText(key.subject()) && subjectCount >= subjectLimit) {
                record("blocked", ipSource);
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
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

    public void resetSubject(String subject) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(subject, null);
            if (StringUtils.hasText(key.subject())) {
                loginRateLimitRepository.delete(subjectKey(key.subject()));
            }
        } catch (RuntimeException e) {
            record("dependency_error", null);
            log.warn("[auth][login-rate-limit] resetSubject failed: {}", e.toString());
        }
    }

    public PasswordCheckPermit acquirePasswordCheck(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return PasswordCheckPermit.none();
        }
        LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);
        List<Slot> slots = new ArrayList<>(2);
        if (StringUtils.hasText(key.ip())) {
            String failureKey = ipKey(key);
            slots.add(new Slot(failureKey, inFlightKey(failureKey),
                    Math.max(1, properties.getMaxFailuresPerIp())));
        }
        if (StringUtils.hasText(key.subject())) {
            String failureKey = inputKey(key.subject());
            slots.add(new Slot(failureKey, inFlightKey(failureKey),
                    Math.max(1, properties.getMaxFailuresPerUser())));
        }
        if (slots.isEmpty()) {
            return PasswordCheckPermit.none();
        }

        UUID token = UUID.randomUUID();
        List<String> acquired = new ArrayList<>(slots.size());
        int leaseSeconds = Math.max(30, properties.getPasswordCheckLeaseSeconds());
        int leaseMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, leaseSeconds * 1_000L));
        try {
            for (Slot slot : slots) {
                if (!loginRateLimitRepository.tryAcquire(
                        slot.failureKey(), slot.leaseKey(), token,
                        slot.limit(), leaseMillis)) {
                    releaseKeys(acquired, token);
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                            "并发登录尝试过多，请稍后再试");
                }
                acquired.add(slot.leaseKey());
            }
            PasswordCheckPermit permit = new PasswordCheckPermit(token, acquired, leaseMillis);
            scheduleRenewal(permit);
            record("allowed", ipSource);
            return permit;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            releaseKeys(acquired, token);
            record("dependency_error", ipSource);
            log.warn("[auth][login-rate-limit] password-check acquire failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "登录风控暂时不可用，请稍后重试");
        }
    }

    public void attachAuthenticationSubject(
            PasswordCheckPermit permit,
            String provisionalInput,
            String authoritativeSubject,
            String ipSource
    ) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            if (permit == null) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                        "登录风控租约已失效，请稍后重试");
            }
            LoginRateLimitKey input = loginRateLimitDomainService.keyOf(provisionalInput, null);
            LoginRateLimitKey subject = loginRateLimitDomainService.keyOf(authoritativeSubject, null);
            if (!StringUtils.hasText(input.subject()) || !StringUtils.hasText(subject.subject())) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                        "登录风控身份无效，请稍后重试");
            }
            synchronized (permit) {
                if (!permit.isOpen() || permit.token() == null) {
                    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                            "登录风控租约已失效，请稍后重试");
                }
                if (!renewOwnedSlots(permit)) {
                    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                            "登录风控租约已失效，请稍后重试");
                }

                String provisionalLeaseKey = inFlightKey(inputKey(input.subject()));
                List<String> ownedKeys = permit.keys();
                if (!ownedKeys.contains(provisionalLeaseKey)) {
                    permit.invalidate();
                    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                            "登录风控租约状态异常，请稍后重试");
                }

                String subjectFailureKey = subjectKey(subject.subject());
                String subjectLeaseKey = inFlightKey(subjectFailureKey);
                if (!loginRateLimitRepository.tryAcquire(
                        subjectFailureKey,
                        subjectLeaseKey,
                        permit.token(),
                        Math.max(1, properties.getMaxFailuresPerUser()),
                        permit.leaseMillis()
                )) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                            "并发登录尝试过多，请稍后再试");
                }

                List<String> authoritativeKeys = new ArrayList<>(ownedKeys.size());
                for (String key : ownedKeys) {
                    if (!key.equals(provisionalLeaseKey)) {
                        authoritativeKeys.add(key);
                    }
                }
                authoritativeKeys.add(subjectLeaseKey);
                permit.replaceKeys(authoritativeKeys);
                releaseKeys(List.of(provisionalLeaseKey), permit.token());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            record("dependency_error", ipSource);
            log.warn("[auth][login-rate-limit] authentication subject attach failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "登录风控暂时不可用，请稍后重试");
        }
    }

    public void releasePasswordCheck(PasswordCheckPermit permit) {
        if (permit == null) {
            return;
        }
        List<String> keys;
        synchronized (permit) {
            if (permit.keys().isEmpty()) {
                return;
            }
            permit.close();
            keys = permit.keys();
        }
        releaseKeys(keys, permit.token());
    }

    public void assertPasswordCheckOwned(PasswordCheckPermit permit) {
        if (permit == null || permit.keys().isEmpty()) {
            return;
        }
        if (!permit.isOpen() || !renewOwnedSlots(permit)) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "登录风控租约已失效，请稍后重试");
        }
    }

    public boolean isCaptchaRequired(String username, String ip) {
        return isCaptchaRequired(username, ip, null);
    }

    public boolean isCaptchaRequired(
            String subject,
            String ip,
            PasswordCheckPermit permit
    ) {
        if (!properties.isEnabled()) {
            return false;
        }

        try {
            int ipThreshold = properties.getCaptchaRequiredFailuresPerIp();
            int subjectThreshold = properties.getCaptchaRequiredFailuresPerUser();
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(subject, ip);

            if (StringUtils.hasText(key.ip())) {
                int count = getBudgetCount(ipKey(key), permit);
                if (ipThreshold <= 0 || count >= ipThreshold) {
                    return true;
                }
            }
            if (StringUtils.hasText(key.subject())) {
                int count = getBudgetCount(subjectKey(key.subject()), permit);
                if (subjectThreshold <= 0 || count >= subjectThreshold) {
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

    private int getBudgetCount(String failureKey, PasswordCheckPermit permit) {
        String leaseKey = inFlightKey(failureKey);
        if (permit == null || !permit.isOpen() || !permit.keys().contains(leaseKey)) {
            return getCount(failureKey);
        }
        return loginRateLimitRepository.countBudget(failureKey, leaseKey);
    }

    private int increment(String key) {
        int windowSeconds = Math.max(1, properties.getWindowSeconds());
        return loginRateLimitRepository.increment(key, windowSeconds);
    }

    private void scheduleRenewal(PasswordCheckPermit permit) {
        long intervalMillis = Math.max(1L, permit.leaseMillis() / 4L);
        ScheduledFuture<?> future = leaseRenewer.scheduleWithFixedDelay(
                () -> renew(permit),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
        permit.attach(future);
    }

    private void renew(PasswordCheckPermit permit) {
        if (permit == null || !permit.isOpen()) {
            return;
        }
        renewOwnedSlots(permit);
    }

    private boolean renewOwnedSlots(PasswordCheckPermit permit) {
        synchronized (permit) {
            if (!permit.isOpen()) {
                return false;
            }
            for (String key : permit.keys()) {
                try {
                    if (!loginRateLimitRepository.renew(key, permit.token(), permit.leaseMillis())) {
                        permit.invalidate();
                        record("lease_lost", null);
                        return false;
                    }
                } catch (RuntimeException e) {
                    permit.invalidate();
                    record("dependency_error", null);
                    log.warn("[auth][login-rate-limit] password-check renewal failed: {}", e.toString());
                    return false;
                }
            }
            return true;
        }
    }

    private void releaseKeys(List<String> keys, UUID token) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> reversed = new ArrayList<>(keys);
        Collections.reverse(reversed);
        for (String key : reversed) {
            try {
                loginRateLimitRepository.release(key, token);
            } catch (RuntimeException e) {
                // Lease TTL bounds a cleanup failure; authentication outcome
                // must not be changed after the password check completed.
                record("dependency_error", null);
                log.warn("[auth][login-rate-limit] password-check release failed: {}", e.toString());
            }
        }
    }

    private String ipKey(LoginRateLimitKey key) {
        return KEY_PREFIX_IP + redisKeyComponent("v2", "ip", key.ip());
    }

    private String inputKey(String input) {
        return KEY_PREFIX_INPUT + redisKeyComponent("v3", "input", input);
    }

    private String subjectKey(String subject) {
        return KEY_PREFIX_SUBJECT + redisKeyComponent("v3", "subject", subject);
    }

    private String inFlightKey(String failureKey) {
        return IN_FLIGHT_KEY_PREFIX + "{" + failureKey + "}:" + failureKey;
    }

    private String redisKeyComponent(String version, String scope, String value) {
        return version + "-" + identifierDeriver.identifierId("login-" + scope, value);
    }

    public static final class PasswordCheckPermit {

        private final UUID token;
        private final AtomicReference<List<String>> keys;
        private final int leaseMillis;
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicReference<ScheduledFuture<?>> renewal = new AtomicReference<>();

        public PasswordCheckPermit(UUID token, List<String> keys) {
            this(token, keys, 0);
        }

        private PasswordCheckPermit(UUID token, List<String> keys, int leaseMillis) {
            this.token = token;
            this.keys = new AtomicReference<>(keys == null ? List.of() : List.copyOf(keys));
            this.leaseMillis = leaseMillis;
        }

        public UUID token() {
            return token;
        }

        public List<String> keys() {
            return keys.get();
        }

        void replaceKeys(List<String> replacement) {
            keys.set(replacement == null ? List.of() : List.copyOf(replacement));
        }

        int leaseMillis() {
            return leaseMillis;
        }

        boolean isOpen() {
            return valid.get() && !closed.get();
        }

        void attach(ScheduledFuture<?> future) {
            if (!renewal.compareAndSet(null, future) && future != null) {
                future.cancel(false);
            }
            if (closed.get() && future != null) {
                future.cancel(false);
            }
        }

        void invalidate() {
            valid.set(false);
            ScheduledFuture<?> future = renewal.get();
            if (future != null) {
                future.cancel(false);
            }
        }

        void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> future = renewal.get();
            if (future != null) {
                future.cancel(false);
            }
        }

        static PasswordCheckPermit none() {
            return new PasswordCheckPermit(null, List.of(), 0);
        }
    }

    private record Slot(String failureKey, String leaseKey, int limit) {
    }
}
