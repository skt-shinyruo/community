package com.nowcoder.community.social.follow;

import com.nowcoder.community.social.follow.dto.FollowItem;

import java.util.List;

public interface FollowRepository {

    boolean follow(int userId, int entityType, int entityId, long followTimeMillis);

    boolean unfollow(int userId, int entityType, int entityId);

    boolean hasFollowed(int userId, int entityType, int entityId);

    long countFollowees(int userId, int entityType);

    long countFollowers(int entityType, int entityId);

    List<FollowItem> listFollowees(int userId, int entityType, int offset, int limit);

    List<FollowItem> listFollowers(int entityType, int entityId, int offset, int limit);
}

