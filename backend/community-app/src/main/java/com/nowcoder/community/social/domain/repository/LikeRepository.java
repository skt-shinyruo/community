package com.nowcoder.community.social.domain.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface LikeRepository {

    boolean addLike(UUID userId, int entityType, UUID entityId);

    boolean removeLike(UUID userId, int entityType, UUID entityId);

    boolean isLiked(UUID userId, int entityType, UUID entityId);

    long countEntityLikes(int entityType, UUID entityId);

    long incrementUserLikeCount(UUID userId, long delta);

    long getUserLikeCount(UUID userId);

    default boolean requiresExplicitCompensation() {
        return false;
    }

    /**
     * 目标状态写入（set 语义）：将“实体点赞关系”与“被赞用户获赞计数”更新收敛为一个仓储操作。
     *
     * <p>说明：</p>
     * <ul>
     *   <li>默认实现基于 {@link #addLike}/{@link #removeLike} + {@link #incrementUserLikeCount} 组合。</li>
     *   <li>Redis 实现可重写为 Lua 脚本，以确保跨 key 原子性。</li>
     * </ul>
     *
     * @param actorUserId  点赞/取消点赞的用户
     * @param entityType   实体类型
     * @param entityId     实体 ID
     * @param entityUserId 实体归属用户（被赞用户）；若未知/不适用可传 0，此时不更新获赞计数
     * @param liked        目标状态：true=点赞，false=取消点赞
     * @return true 表示状态发生变更（从无到有/从有到无）；false 表示幂等 no-op
     */
    default boolean setLike(UUID actorUserId, int entityType, UUID entityId, UUID entityUserId, boolean liked) {
        if (liked) {
            boolean added = addLike(actorUserId, entityType, entityId);
            if (added && entityUserId != null) {
                incrementUserLikeCount(entityUserId, 1);
            }
            return added;
        }
        boolean removed = removeLike(actorUserId, entityType, entityId);
        if (removed && entityUserId != null) {
            incrementUserLikeCount(entityUserId, -1);
        }
        return removed;
    }

    default Map<UUID, Long> countEntityLikesBatch(int entityType, List<UUID> entityIds) {
        Map<UUID, Long> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (UUID id : entityIds) {
            if (id == null) {
                continue;
            }
            out.put(id, countEntityLikes(entityType, id));
        }
        return out;
    }

    default Map<UUID, Boolean> likedStatusesBatch(UUID userId, int entityType, List<UUID> entityIds) {
        Map<UUID, Boolean> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (UUID id : entityIds) {
            if (id == null) {
                continue;
            }
            out.put(id, isLiked(userId, entityType, id));
        }
        return out;
    }
}
