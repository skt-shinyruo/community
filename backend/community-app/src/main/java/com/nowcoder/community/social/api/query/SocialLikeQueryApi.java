package com.nowcoder.community.social.api.query;

public interface SocialLikeQueryApi {

    boolean isLiked(int actorUserId, int entityType, int entityId);

    long count(int entityType, int entityId);

    long userLikeCount(int userId);
}
