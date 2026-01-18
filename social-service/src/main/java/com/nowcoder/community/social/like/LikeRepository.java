package com.nowcoder.community.social.like;

public interface LikeRepository {

    boolean addLike(int userId, int entityType, int entityId);

    boolean removeLike(int userId, int entityType, int entityId);

    boolean isLiked(int userId, int entityType, int entityId);

    long countEntityLikes(int entityType, int entityId);

    long incrementUserLikeCount(int userId, long delta);

    long getUserLikeCount(int userId);
}

