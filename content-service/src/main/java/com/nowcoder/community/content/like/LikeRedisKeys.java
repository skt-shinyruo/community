package com.nowcoder.community.content.like;

import org.springframework.util.StringUtils;

/**
 * 点赞 Redis key 生成器（SSOT）：避免读写两侧各自拼 key 导致口径不一致。
 */
public final class LikeRedisKeys {

    private LikeRedisKeys() {
    }

    public static String entityKey(int entityType, int entityId) {
        int t = Math.max(0, entityType);
        int id = Math.max(0, entityId);
        return "like:entity:" + t + ":" + id;
    }

    public static String entityKey(String entityType, String entityId) {
        String t = StringUtils.hasText(entityType) ? entityType.trim() : "0";
        String id = StringUtils.hasText(entityId) ? entityId.trim() : "0";
        return "like:entity:" + t + ":" + id;
    }
}

