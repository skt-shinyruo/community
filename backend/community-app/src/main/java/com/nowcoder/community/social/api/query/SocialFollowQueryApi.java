package com.nowcoder.community.social.api.query;

public interface SocialFollowQueryApi {

    boolean hasFollowed(int actorUserId, int entityType, int entityId);

    long followeeCount(int userId, int entityType);

    long followerCount(int entityType, int entityId);
}
