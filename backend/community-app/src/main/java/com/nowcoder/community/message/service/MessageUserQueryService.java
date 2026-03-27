package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.UserSummary;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.service.UserQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageUserQueryService {

    private final Duration resolveCacheTtl;
    private final int resolveCacheMaxSize;
    private final UserQueryService userQueryService;
    private final ConcurrentHashMap<String, ResolveCacheEntry> resolveCache = new ConcurrentHashMap<>();

    public MessageUserQueryService(
            @Value("${message.user-lookup.resolve-cache.ttl:60s}") Duration resolveCacheTtl,
            @Value("${message.user-lookup.resolve-cache.max-size:5000}") int resolveCacheMaxSize,
            UserQueryService userQueryService
    ) {
        this.resolveCacheTtl = resolveCacheTtl == null || resolveCacheTtl.isNegative() ? Duration.ofSeconds(60) : resolveCacheTtl;
        this.resolveCacheMaxSize = Math.max(0, resolveCacheMaxSize);
        this.userQueryService = userQueryService;
    }

    public Integer findUserIdByUsernameOrNull(String username) {
        UserSummary userSummary = findUserSummaryByUsernameOrNull(username);
        return userSummary == null || userSummary.getId() <= 0 ? null : userSummary.getId();
    }

    public UserSummary findUserSummaryByIdOrNull(int userId) {
        if (userId <= 0) {
            return null;
        }
        try {
            return toSummary(userQueryService.getById(userId));
        } catch (BusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    public Map<Integer, UserSummary> getUserSummariesByIds(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = userIds.stream()
                .filter(id -> id != null && id > 0)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = userQueryService.listUserSummariesByIds(ids);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Map<Integer, UserSummary> userSummaries = new ConcurrentHashMap<>(users.size());
        for (User user : users) {
            UserSummary summary = toSummary(user);
            if (summary == null || summary.getId() <= 0) {
                continue;
            }
            userSummaries.put(summary.getId(), summary);
        }
        return userSummaries;
    }

    private UserSummary findUserSummaryByUsernameOrNull(String username) {
        String key = normalizeUsernameKey(username);
        if (!StringUtils.hasText(key)) {
            return null;
        }

        UserSummary cached = getResolveCache(key);
        if (cached != null) {
            return cached;
        }

        UserSummary summary;
        try {
            summary = toSummary(userQueryService.getByUsername(key));
        } catch (BusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw e;
        }
        if (summary != null && summary.getId() > 0 && StringUtils.hasText(summary.getUsername())) {
            putResolveCache(summary.getUsername(), summary);
        }
        return summary;
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
        String normalizedKey = normalizeUsernameKey(key);
        if (!StringUtils.hasText(normalizedKey) || value == null || value.getId() <= 0) {
            return;
        }
        if (resolveCacheMaxSize <= 0) {
            return;
        }
        if (resolveCache.size() >= resolveCacheMaxSize) {
            cleanupResolveCache();
            if (resolveCache.size() >= resolveCacheMaxSize) {
                resolveCache.clear();
            }
        }
        long expiresAt = System.currentTimeMillis() + Math.max(0L, resolveCacheTtl.toMillis());
        resolveCache.put(normalizedKey, new ResolveCacheEntry(value, expiresAt));
    }

    private void cleanupResolveCache() {
        if (resolveCache.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        resolveCache.entrySet().removeIf(entry ->
                entry == null || entry.getValue() == null || entry.getValue().expiresAtEpochMs <= now
        );
    }

    private record ResolveCacheEntry(UserSummary value, long expiresAtEpochMs) {
    }
}
