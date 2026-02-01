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
