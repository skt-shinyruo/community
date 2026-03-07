package com.nowcoder.community.message.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.infra.internalclient.InternalCallOptions;
import com.nowcoder.community.infra.internalclient.InternalClientSupport;
import com.nowcoder.community.user.api.rpc.UserReadRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private static final String SERVICE_NAME = "user-service";

    private final MeterRegistry meterRegistry;
    private final boolean failOpen;
    private final UserReadRpcService userReadRpcService;

    // username -> userId 解析：用于 toName 写路径的查询放大控制（短 TTL、容量受控）。
    private final Duration resolveCacheTtl;
    private final int resolveCacheMaxSize;
    private final ConcurrentHashMap<String, ResolveCacheEntry> resolveCache = new ConcurrentHashMap<>();

    public UserServiceClient(
            MeterRegistry meterRegistry,
            @Value("${clients.user.fail-open:false}") boolean failOpen,
            @Value("${clients.user.resolve-cache.ttl:60s}") Duration resolveCacheTtl,
            @Value("${clients.user.resolve-cache.max-size:5000}") int resolveCacheMaxSize,
            UserReadRpcService userReadRpcService
    ) {
        this.meterRegistry = meterRegistry;
        this.failOpen = failOpen;
        this.resolveCacheTtl = resolveCacheTtl == null || resolveCacheTtl.isNegative() ? Duration.ofSeconds(60) : resolveCacheTtl;
        this.resolveCacheMaxSize = Math.max(0, resolveCacheMaxSize);
        this.userReadRpcService = userReadRpcService;
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

        UserSummary data = callResult("resolveByUsername", () -> userReadRpcService.resolveByUsernameOrNull(key), () -> null);
        if (data != null && data.getId() > 0 && StringUtils.hasText(data.getUsername())) {
            putResolveCache(data.getUsername(), data);
        }
        return data;
    }

    public UserSummary getById(int userId) {
        if (userId <= 0) {
            return null;
        }
        return callResult("getById", () -> userReadRpcService.getByIdOrNull(userId), () -> null);
    }

    public Map<Integer, UserSummary> safeBatchGetUsers(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = userIds.stream().filter(id -> id != null && id > 0).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UserSummary> list = callResult("batchSummary", () -> userReadRpcService.batchSummary(ids), () -> List.of());
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

    private <T> T callResult(String api, Supplier<Result<T>> supplier, Supplier<T> fallback) {
        InternalCallOptions<T> options = (failOpen && fallback != null)
                ? InternalCallOptions.failOpen(fallback)
                : InternalCallOptions.failClosed();
        return InternalClientSupport.callResult(
                meterRegistry,
                SERVICE_NAME,
                api,
                supplier,
                options.withWarnLogger((m, e) -> log.warn(m, e))
        );
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
