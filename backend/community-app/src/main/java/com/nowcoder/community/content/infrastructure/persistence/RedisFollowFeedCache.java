package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.CacheTtlPolicy;
import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.FollowFeedCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisFollowFeedCache implements FollowFeedCache {

    private static final String FOLLOW_FEED_KEY_PREFIX = "post:feed:follow:";
    private static final Duration FOLLOW_FEED_PAGE_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;
    private final CacheTtlPolicy ttlPolicy;
    private final ContentHotPathProperties hotPathProperties;

    public RedisFollowFeedCache(StringRedisTemplate redisTemplate, JsonCodec jsonCodec) {
        this(redisTemplate, jsonCodec, fixedTtlPolicy(), fixedProperties());
    }

    @Autowired
    public RedisFollowFeedCache(
            StringRedisTemplate redisTemplate,
            JsonCodec jsonCodec,
            CacheTtlPolicy ttlPolicy,
            ContentHotPathProperties hotPathProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
        this.ttlPolicy = ttlPolicy == null ? new CacheTtlPolicy(new ContentHotPathProperties()) : ttlPolicy;
        this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
    }

    @Override
    public FollowFeedPageSlice getOrLoadPage(UUID userId, String cursor, int size, Supplier<FollowFeedPageSlice> loader) {
        if (userId == null) {
            return new FollowFeedPageSlice(List.of(), null, null);
        }
        String safeCursor = StringUtils.hasText(cursor) ? cursor.trim() : "";
        int safeSize = Math.max(1, size);
        String key = key(userId, safeCursor, safeSize);
        CachePage cached = readCachedPage(key);
        if (cached.hit()) {
            return cached.page();
        }
        FollowFeedPageSlice loaded = sanitize(loader == null ? null : loader.get());
        redisTemplate.opsForValue().set(
                key,
                jsonCodec.toJson(serialize(loaded)),
                ttlPolicy.jitteredTtl(key, hotPathProperties.getCache().followPageTtl())
        );
        return loaded;
    }

    private CachePage readCachedPage(String key) {
        String raw = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) {
            return CachePage.miss();
        }
        try {
            JsonNode node = jsonCodec.readTree(raw);
            if (node == null) {
                redisTemplate.delete(key);
                return CachePage.miss();
            }
            if (node.isArray()) {
                return CachePage.hit(new FollowFeedPageSlice(readIds(node), null, null));
            }
            JsonNode idsNode = node.path("ids");
            if (!idsNode.isArray()) {
                redisTemplate.delete(key);
                return CachePage.miss();
            }
            Date anchorCreateTime = null;
            JsonNode anchorTimeNode = node.get("anchorCreateTimeMillis");
            if (anchorTimeNode != null && anchorTimeNode.canConvertToLong()) {
                anchorCreateTime = new Date(anchorTimeNode.asLong());
            }
            UUID anchorPostId = null;
            JsonNode anchorPostIdNode = node.get("anchorPostId");
            if (anchorPostIdNode != null && anchorPostIdNode.isTextual()) {
                anchorPostId = parseUuid(anchorPostIdNode.asText());
            }
            return CachePage.hit(new FollowFeedPageSlice(readIds(idsNode), anchorCreateTime, anchorPostId));
        } catch (JsonCodecException ex) {
            redisTemplate.delete(key);
            return CachePage.miss();
        }
    }

    private List<UUID> readIds(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isTextual()) {
                continue;
            }
            UUID parsed = parseUuid(item.asText());
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        return List.copyOf(ids);
    }

    private Map<String, Object> serialize(FollowFeedPageSlice page) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ids", page.ids().stream().map(UUID::toString).toList());
        if (page.anchorCreateTime() != null && page.anchorPostId() != null) {
            payload.put("anchorCreateTimeMillis", page.anchorCreateTime().getTime());
            payload.put("anchorPostId", page.anchorPostId().toString());
        }
        return payload;
    }

    private FollowFeedPageSlice sanitize(FollowFeedPageSlice loaded) {
        if (loaded == null) {
            return new FollowFeedPageSlice(List.of(), null, null);
        }
        List<UUID> ids = loaded.ids() == null ? List.of() : loaded.ids().stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        Date anchorCreateTime = loaded.anchorCreateTime();
        UUID anchorPostId = loaded.anchorPostId();
        if (anchorCreateTime == null || anchorPostId == null) {
            return new FollowFeedPageSlice(ids, null, null);
        }
        if (!ids.contains(anchorPostId) && !ids.isEmpty()) {
            return new FollowFeedPageSlice(ids, null, null);
        }
        return new FollowFeedPageSlice(ids, anchorCreateTime, anchorPostId);
    }

    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String key(UUID userId, String cursor, int size) {
        return FOLLOW_FEED_KEY_PREFIX + userId + ":cursor:" + (StringUtils.hasText(cursor) ? cursor : "initial") + ":size:" + size;
    }

    private static ContentHotPathProperties fixedProperties() {
        ContentHotPathProperties properties = new ContentHotPathProperties();
        properties.getCache().setFollowPageTtlSeconds(FOLLOW_FEED_PAGE_TTL.toSeconds());
        properties.getCache().setTtlJitterSeconds(0L);
        return properties;
    }

    private static CacheTtlPolicy fixedTtlPolicy() {
        return new CacheTtlPolicy(fixedProperties());
    }

    private record CachePage(boolean hit, FollowFeedPageSlice page) {

        private static CachePage hit(FollowFeedPageSlice page) {
            return new CachePage(true, page == null ? new FollowFeedPageSlice(List.of(), null, null) : page);
        }

        private static CachePage miss() {
            return new CachePage(false, new FollowFeedPageSlice(List.of(), null, null));
        }
    }
}
