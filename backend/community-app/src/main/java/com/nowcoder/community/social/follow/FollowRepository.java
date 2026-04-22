package com.nowcoder.community.social.follow;

import com.nowcoder.community.social.follow.dto.FollowItem;

import java.util.List;
import java.util.UUID;

public interface FollowRepository {

    boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis);

    boolean unfollow(UUID userId, int entityType, UUID entityId);

    boolean hasFollowed(UUID userId, int entityType, UUID entityId);

    long countFollowees(UUID userId, int entityType);

    long countFollowers(int entityType, UUID entityId);

    List<FollowItem> listFollowees(UUID userId, int entityType, int offset, int limit);

    List<FollowItem> listFollowers(int entityType, UUID entityId, int offset, int limit);

    default boolean requiresExplicitCompensation() {
        return false;
    }
}
