package com.nowcoder.community.social.api.query;

import java.util.List;
import java.util.UUID;

public interface SocialFollowQueryApi {

    boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId);

    long followeeCount(UUID userId, int entityType);

    long followerCount(int entityType, UUID entityId);

    List<UUID> listFolloweeIds(UUID userId, int limit);
}
