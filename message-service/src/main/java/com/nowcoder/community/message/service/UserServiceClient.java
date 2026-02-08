package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import com.nowcoder.community.message.service.dto.UserSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.time.Duration;

@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private static final String METRIC_CLIENT = "message-service:user-service";

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final boolean failOpen;

    // username -> userId 解析：用于 toName 写路径的依赖放大控制（短 TTL、容量受控）。
    private final Duration resolveCacheTtl;
    private final int resolveCacheMaxSize;
    private final ConcurrentHashMap<String, ResolveCacheEntry> resolveCache = new ConcurrentHashMap<>();

    public UserServiceClient(
            RestTemplate restTemplate,
            MeterRegistry meterRegistry,
            @Value("${clients.user.base-url:http://user-service}") String baseUrl,
            @Value("${clients.user.fail-open:false}") boolean failOpen,
            @Value("${clients.user.resolve-cache.ttl:60s}") Duration resolveCacheTtl,
            @Value("${clients.user.resolve-cache.max-size:5000}") int resolveCacheMaxSize
    ) {
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.baseUrl = baseUrl;
        this.failOpen = failOpen;
        this.resolveCacheTtl = resolveCacheTtl == null || resolveCacheTtl.isNegative() ? Duration.ofSeconds(60) : resolveCacheTtl;
        this.resolveCacheMaxSize = Math.max(0, resolveCacheMaxSize);
    }

    public Integer safeResolveUserIdByUsername(String username) {
        return call("resolveByUsername", () -> {
            UserSummary u = resolveByUsername(username);
            return u == null || u.getId() <= 0 ? null : u.getId();
        }, () -> null);
    }

    public UserSummary safeGetUser(int userId) {
        return call("getById", () -> getById(userId), () -> null);
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

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/users/resolve")
                .queryParam("username", key)
                .toUriString();
        Result<UserSummary> result = exchange(url, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<Result<UserSummary>>() {
        });
        UserSummary data = result == null ? null : result.getData();
        if (data != null && data.getId() > 0) {
            putResolveCache(key, data);
        }
        return data;
    }

    public UserSummary getById(int userId) {
        if (userId <= 0) {
            return null;
        }
        String url = baseUrl + "/api/users/" + userId;
        Result<UserSummary> result = exchange(url, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<Result<UserSummary>>() {
        });
        return result == null ? null : result.getData();
    }

    public Map<Integer, UserSummary> safeBatchGetUsers(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return call("batchSummary", () -> batchSummary(userIds), () -> Map.of());
    }

    private Map<Integer, UserSummary> batchSummary(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = userIds.stream().filter(id -> id != null && id > 0).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        BatchUserSummaryRequest req = new BatchUserSummaryRequest();
        req.setUserIds(ids);

        String url = baseUrl + "/internal/users/batch-summary";
        ResponseEntity<Result<List<UserSummary>>> resp = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(req, InternalClientSupport.jsonHeaders()),
                new ParameterizedTypeReference<Result<List<UserSummary>>>() {
                }
        );
        List<UserSummary> list = InternalClientSupport.unwrap(resp, "user-service");
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<Integer, UserSummary> map = new HashMap<>(list.size());
        for (UserSummary u : list) {
            if (u != null && u.getId() > 0) {
                map.put(u.getId(), u);
            }
        }
        return map;
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_SUCCESS, start);
            return v;
        } catch (RuntimeException e) {
            if (fallback != null && failOpen) {
                InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api={}): {}", api, e.toString());
                return fallback.get();
            }
            String outcome = InternalClientSupport.isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, outcome, start);
            throw e;
        }
    }

    private <T> Result<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, ParameterizedTypeReference<Result<T>> typeRef) {
        try {
            ResponseEntity<Result<T>> resp = restTemplate.exchange(url, method, entity, typeRef);
            return resp.getBody();
        } catch (RestClientException e) {
            throw e;
        }
    }

    public static class BatchUserSummaryRequest {
        private List<Integer> userIds;

        public List<Integer> getUserIds() {
            return userIds == null ? List.of() : userIds;
        }

        public void setUserIds(List<Integer> userIds) {
            this.userIds = userIds;
        }
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
