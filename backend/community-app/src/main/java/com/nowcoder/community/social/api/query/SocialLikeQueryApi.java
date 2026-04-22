package com.nowcoder.community.social.api.query;

import java.util.UUID;

public interface SocialLikeQueryApi {

    boolean isLiked(UUID actorUserId, int entityType, UUID entityId);

    long count(int entityType, UUID entityId);

    long userLikeCount(UUID userId);
}
