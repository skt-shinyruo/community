package com.nowcoder.community.message.app;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.message.dto.SendMessageRequest;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageRecipientResolver {

    private final Duration resolveCacheTtl;
    private final int resolveCacheMaxSize;
    private final UserLookupQueryApi userLookupQueryApi;
    private final ConcurrentHashMap<String, ResolveCacheEntry> resolveCache = new ConcurrentHashMap<>();

    @Autowired
    public MessageRecipientResolver(
            @Value("${message.user-lookup.resolve-cache.ttl:60s}") Duration resolveCacheTtl,
            @Value("${message.user-lookup.resolve-cache.max-size:5000}") int resolveCacheMaxSize,
            UserLookupQueryApi userLookupQueryApi
    ) {
        this.resolveCacheTtl = resolveCacheTtl == null || resolveCacheTtl.isNegative()
                ? Duration.ofSeconds(60)
                : resolveCacheTtl;
        this.resolveCacheMaxSize = Math.max(0, resolveCacheMaxSize);
        this.userLookupQueryApi = userLookupQueryApi;
    }

    MessageRecipientResolver(UserLookupQueryApi userLookupQueryApi) {
        this(Duration.ofSeconds(60), 5000, userLookupQueryApi);
    }

    public int resolveToUserId(SendMessageRequest request) {
        Integer toId = request == null ? null : request.getToId();
        if (toId != null && toId > 0) {
            userLookupQueryApi.requireSummaryById(toId);
            return toId;
        }

        String toName = request == null ? null : request.getToName();
        if (!StringUtils.hasText(toName)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "toId/toName 至少提供一个");
        }

        String normalizedToName = toName.trim();
        Integer cachedRecipientId = getResolveCache(normalizedToName);
        if (cachedRecipientId != null && cachedRecipientId > 0) {
            return cachedRecipientId;
        }

        int recipientId = userLookupQueryApi.requireSummaryByUsername(normalizedToName).id();
        putResolveCache(normalizedToName, recipientId);
        return recipientId;
    }

    private Integer getResolveCache(String key) {
        String normalizedKey = normalizeUsernameKey(key);
        if (!StringUtils.hasText(normalizedKey) || resolveCacheMaxSize <= 0) {
            return null;
        }
        ResolveCacheEntry entry = resolveCache.get(normalizedKey);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochMs <= System.currentTimeMillis()) {
            resolveCache.remove(normalizedKey);
            return null;
        }
        return entry.userId;
    }

    private void putResolveCache(String key, int userId) {
        String normalizedKey = normalizeUsernameKey(key);
        if (!StringUtils.hasText(normalizedKey) || userId <= 0 || resolveCacheMaxSize <= 0) {
            return;
        }
        if (resolveCache.size() >= resolveCacheMaxSize) {
            cleanupResolveCache();
            if (resolveCache.size() >= resolveCacheMaxSize) {
                resolveCache.clear();
            }
        }
        long expiresAt = System.currentTimeMillis() + Math.max(0L, resolveCacheTtl.toMillis());
        resolveCache.put(normalizedKey, new ResolveCacheEntry(userId, expiresAt));
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

    private String normalizeUsernameKey(String username) {
        return StringUtils.hasText(username) ? username.trim() : "";
    }

    private record ResolveCacheEntry(int userId, long expiresAtEpochMs) {
    }
}
