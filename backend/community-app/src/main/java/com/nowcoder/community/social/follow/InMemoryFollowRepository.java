package com.nowcoder.community.social.follow;

import com.nowcoder.community.social.follow.dto.FollowItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "memory")
public class InMemoryFollowRepository implements FollowRepository {

    private final Map<String, Map<UUID, Long>> followees = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> followers = new ConcurrentHashMap<>();

    @Override
    public boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis) {
        Map<UUID, Long> followeeMap = followees.computeIfAbsent(followeeKey(userId, entityType), ignored -> new ConcurrentHashMap<>());
        Long existed = followeeMap.putIfAbsent(entityId, followTimeMillis);
        if (existed != null) {
            // 幂等：若历史/异常导致 follower 缺失，则尽量补齐（不重复返回 created=true）。
            followers.computeIfAbsent(followerKey(entityType, entityId), ignored -> new ConcurrentHashMap<>()).putIfAbsent(userId, existed);
            return false;
        }
        followers.computeIfAbsent(followerKey(entityType, entityId), ignored -> new ConcurrentHashMap<>()).put(userId, followTimeMillis);
        return true;
    }

    @Override
    public boolean unfollow(UUID userId, int entityType, UUID entityId) {
        Map<UUID, Long> followeeMap = followees.get(followeeKey(userId, entityType));
        boolean removed = followeeMap != null && followeeMap.remove(entityId) != null;
        Map<UUID, Long> followerMap = followers.get(followerKey(entityType, entityId));
        if (followerMap != null) {
            followerMap.remove(userId);
        }
        return removed;
    }

    @Override
    public boolean hasFollowed(UUID userId, int entityType, UUID entityId) {
        Map<UUID, Long> followeeMap = followees.get(followeeKey(userId, entityType));
        return followeeMap != null && followeeMap.containsKey(entityId);
    }

    @Override
    public long countFollowees(UUID userId, int entityType) {
        Map<UUID, Long> map = followees.get(followeeKey(userId, entityType));
        return map == null ? 0 : map.size();
    }

    @Override
    public long countFollowers(int entityType, UUID entityId) {
        Map<UUID, Long> map = followers.get(followerKey(entityType, entityId));
        return map == null ? 0 : map.size();
    }

    @Override
    public List<FollowItem> listFollowees(UUID userId, int entityType, int offset, int limit) {
        Map<UUID, Long> map = followees.get(followeeKey(userId, entityType));
        return list(map, offset, limit);
    }

    @Override
    public List<FollowItem> listFollowers(int entityType, UUID entityId, int offset, int limit) {
        Map<UUID, Long> map = followers.get(followerKey(entityType, entityId));
        return list(map, offset, limit);
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }

    private List<FollowItem> list(Map<UUID, Long> map, int offset, int limit) {
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<UUID, Long>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        int from = Math.max(0, offset);
        int to = Math.min(entries.size(), from + Math.max(0, limit));
        List<FollowItem> items = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : entries.subList(from, to)) {
            FollowItem item = new FollowItem();
            item.setTargetId(e.getKey());
            item.setFollowTime(Instant.ofEpochMilli(e.getValue()));
            items.add(item);
        }
        return items;
    }

    private String followeeKey(UUID userId, int entityType) {
        return "followee:" + userId + ":" + entityType;
    }

    private String followerKey(int entityType, UUID entityId) {
        return "follower:" + entityType + ":" + entityId;
    }
}
