package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.dto.UserSummary;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import com.nowcoder.community.user.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.time.Duration;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class UserLookupService {

    private static final Logger log = LoggerFactory.getLogger(UserLookupService.class);
    private static final String INTERNAL_CALL_REQUESTS_TOTAL = "internal_call_requests_total";
    private static final String INTERNAL_CALL_LATENCY = "internal_call_latency";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_ERROR = "error";
    private static final String OUTCOME_TIMEOUT = "timeout";
    private static final String OUTCOME_UNAVAILABLE = "unavailable";
    private static final String OUTCOME_DEGRADED = "degraded";
    private static final String OUTCOME_FORBIDDEN = "forbidden";
    private static final String OUTCOME_REMOTE_ERROR = "remote_error";
    private static final String TARGET_MODULE = "user";

    private final MeterRegistry meterRegistry;
    private final boolean failOpen;
    private final UserService userService;
    private final InternalUserService internalUserService;

    // username -> userId 解析：用于 toName 写路径的查询放大控制（短 TTL、容量受控）。
    private final Duration resolveCacheTtl;
    private final int resolveCacheMaxSize;
    private final ConcurrentHashMap<String, ResolveCacheEntry> resolveCache = new ConcurrentHashMap<>();

    public UserLookupService(
            MeterRegistry meterRegistry,
            @Value("${message.user-lookup.fail-open:false}") boolean failOpen,
            @Value("${message.user-lookup.resolve-cache.ttl:60s}") Duration resolveCacheTtl,
            @Value("${message.user-lookup.resolve-cache.max-size:5000}") int resolveCacheMaxSize,
            UserService userService,
            InternalUserService internalUserService
    ) {
        this.meterRegistry = meterRegistry;
        this.failOpen = failOpen;
        this.resolveCacheTtl = resolveCacheTtl == null || resolveCacheTtl.isNegative() ? Duration.ofSeconds(60) : resolveCacheTtl;
        this.resolveCacheMaxSize = Math.max(0, resolveCacheMaxSize);
        this.userService = userService;
        this.internalUserService = internalUserService;
    }

    public Integer safeResolveUserIdByUsername(String username) {
        UserSummary u = resolveByUsername(username);
        return u == null || u.getId() <= 0 ? null : u.getId();
    }

    public UserSummary safeGetUser(int userId) {
        return getById(userId);
    }

    public UserSummary resolveByUsername(String username) {
        String key = normalizeUsernameKey(username);
        if (!StringUtils.hasText(key)) {
            return null;
        }

        UserSummary cached = getResolveCache(key);
        if (cached != null) {
            return cached;
        }

        UserSummary data = call("resolveByUsername", () -> resolveByUsernameInternal(key), () -> null);
        if (data != null && data.getId() > 0 && StringUtils.hasText(data.getUsername())) {
            putResolveCache(data.getUsername(), data);
        }
        return data;
    }

    public UserSummary getById(int userId) {
        if (userId <= 0) {
            return null;
        }
        return call("getById", () -> getByIdInternal(userId), () -> null);
    }

    public Map<Integer, UserSummary> safeBatchGetUsers(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = userIds.stream().filter(id -> id != null && id > 0).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UserSummary> list = call("batchSummary", () -> batchSummaryInternal(ids), List::of);
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        ConcurrentHashMap<Integer, UserSummary> map = new ConcurrentHashMap<>(list.size());
        for (UserSummary u : list) {
            if (u == null || u.getId() <= 0) {
                continue;
            }
            map.put(u.getId(), u);
        }
        return map;
    }

    private UserSummary resolveByUsernameInternal(String username) {
        try {
            return toSummary(userService.getByUsername(username));
        } catch (BusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private UserSummary getByIdInternal(int userId) {
        try {
            return toSummary(userService.getById(userId));
        } catch (BusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private List<UserSummary> batchSummaryInternal(List<Integer> ids) {
        List<User> users = internalUserService.batchGetUserSummaries(ids);
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .map(this::toSummary)
                .filter(u -> u != null && u.getId() > 0)
                .toList();
    }

    private UserSummary toSummary(User user) {
        if (user == null || user.getId() <= 0) {
            return null;
        }
        UserSummary summary = new UserSummary();
        summary.setId(user.getId());
        summary.setUsername(user.getUsername());
        summary.setHeaderUrl(user.getHeaderUrl());
        return summary;
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T value = supplier.get();
            record(api, OUTCOME_SUCCESS, start);
            return value;
        } catch (RuntimeException e) {
            if (failOpen && fallback != null) {
                record(api, OUTCOME_DEGRADED, start);
                log.warn("[internal-call] module={} api={} outcome={}", TARGET_MODULE, api, OUTCOME_DEGRADED, e);
                return fallback.get();
            }
            if (e instanceof BusinessException be) {
                record(api, classifyBusinessOutcome(be), start);
                throw be;
            }
            String outcome = classifyUnexpectedOutcome(e);
            record(api, outcome, start);
            log.warn("[internal-call] module={} api={} outcome={}", TARGET_MODULE, api, outcome, e);
            throw wrapUnexpectedException(e);
        }
    }

    private void record(String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("module", TARGET_MODULE, "api", String.valueOf(api), "outcome", String.valueOf(outcome));
        meterRegistry.counter(INTERNAL_CALL_REQUESTS_TOTAL, tags).increment();
        meterRegistry.timer(INTERNAL_CALL_LATENCY, tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private String classifyBusinessOutcome(BusinessException e) {
        if (e == null || e.getErrorCode() == null) {
            return OUTCOME_REMOTE_ERROR;
        }
        int httpStatus = e.getErrorCode().getHttpStatus();
        if (httpStatus == CommonErrorCode.FORBIDDEN.getHttpStatus()) {
            return OUTCOME_FORBIDDEN;
        }
        if (httpStatus == CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()) {
            return OUTCOME_UNAVAILABLE;
        }
        if (httpStatus == 504) {
            return OUTCOME_TIMEOUT;
        }
        return OUTCOME_REMOTE_ERROR;
    }

    private String classifyUnexpectedOutcome(Throwable t) {
        if (isTimeout(t)) {
            return OUTCOME_TIMEOUT;
        }
        if (isConnectionError(t)) {
            return OUTCOME_UNAVAILABLE;
        }
        return OUTCOME_ERROR;
    }

    private BusinessException wrapUnexpectedException(Throwable t) {
        if (isTimeout(t) || isConnectionError(t)) {
            return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, TARGET_MODULE + " 不可用", t);
        }
        return new BusinessException(CommonErrorCode.INTERNAL_ERROR, TARGET_MODULE + " 调用失败", t);
    }

    private boolean isTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        if (hasCause(t, SocketTimeoutException.class) || hasCause(t, TimeoutException.class)) {
            return true;
        }
        if (hasCauseByName(t, "java.net.http.HttpTimeoutException")) {
            return true;
        }
        String message = String.valueOf(t.getMessage());
        return containsIgnoreCase(message, "timed out") || containsIgnoreCase(message, "timeout");
    }

    private boolean isConnectionError(Throwable t) {
        if (t == null) {
            return false;
        }
        return hasCause(t, ConnectException.class)
                || hasCause(t, UnknownHostException.class)
                || hasCause(t, NoRouteToHostException.class)
                || hasCause(t, SocketException.class);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(needle)) {
            return false;
        }
        return value.toLowerCase().contains(needle.toLowerCase());
    }

    private boolean hasCauseByName(Throwable t, String className) {
        if (!StringUtils.hasText(className)) {
            return false;
        }
        Throwable cur = t;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        while (cur != null && !seen.containsKey(cur)) {
            seen.put(cur, Boolean.TRUE);
            if (className.equals(cur.getClass().getName())) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        if (t == null || type == null) {
            return false;
        }
        Throwable cur = t;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        while (cur != null && !seen.containsKey(cur)) {
            seen.put(cur, Boolean.TRUE);
            if (type.isInstance(cur)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private String normalizeUsernameKey(String username) {
        return StringUtils.hasText(username) ? username.trim() : "";
    }

    private UserSummary getResolveCache(String key) {
        if (!StringUtils.hasText(key) || resolveCacheMaxSize <= 0) {
            return null;
        }
        ResolveCacheEntry entry = resolveCache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMs <= System.currentTimeMillis()) {
            resolveCache.remove(key);
            return null;
        }
        return entry.value;
    }

    private void putResolveCache(String key, UserSummary value) {
        if (!StringUtils.hasText(key) || value == null || value.getId() <= 0) {
            return;
        }
        if (resolveCacheMaxSize <= 0) {
            return;
        }
        if (resolveCache.size() >= resolveCacheMaxSize) {
            // 容量上限触发时，先尽力清理过期项；仍超限则直接清空（避免复杂 LRU 带来额外依赖）。
            cleanupResolveCache();
            if (resolveCache.size() >= resolveCacheMaxSize) {
                resolveCache.clear();
            }
        }
        long ttlMs = Math.max(0L, resolveCacheTtl.toMillis());
        long expiresAt = System.currentTimeMillis() + ttlMs;
        resolveCache.put(key, new ResolveCacheEntry(value, expiresAt));
    }

    private void cleanupResolveCache() {
        if (resolveCache.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        resolveCache.entrySet().removeIf(e -> e == null || e.getValue() == null || e.getValue().expiresAtEpochMs <= now);
    }

    private record ResolveCacheEntry(UserSummary value, long expiresAtEpochMs) {
    }
}
