package com.nowcoder.community.social.like;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface LikeRepository {

    boolean addLike(int userId, int entityType, int entityId);

    boolean removeLike(int userId, int entityType, int entityId);

    boolean isLiked(int userId, int entityType, int entityId);

    long countEntityLikes(int entityType, int entityId);

    long incrementUserLikeCount(int userId, long delta);

    long getUserLikeCount(int userId);

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
    default boolean setLike(int actorUserId, int entityType, int entityId, int entityUserId, boolean liked) {
        if (liked) {
            boolean added = addLike(actorUserId, entityType, entityId);
            if (added && entityUserId > 0) {
                incrementUserLikeCount(entityUserId, 1);
            }
            return added;
        }
        boolean removed = removeLike(actorUserId, entityType, entityId);
        if (removed && entityUserId > 0) {
            incrementUserLikeCount(entityUserId, -1);
        }
        return removed;
    }

    default Map<Integer, Long> countEntityLikesBatch(int entityType, List<Integer> entityIds) {
        Map<Integer, Long> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (Integer id : entityIds) {
            if (id == null || id <= 0) {
                continue;
            }
            out.put(id, countEntityLikes(entityType, id));
        }
        return out;
    }

    default Map<Integer, Boolean> likedStatusesBatch(int userId, int entityType, List<Integer> entityIds) {
        Map<Integer, Boolean> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (Integer id : entityIds) {
            if (id == null || id <= 0) {
                continue;
            }
            out.put(id, isLiked(userId, entityType, id));
        }
        return out;
    }
}
