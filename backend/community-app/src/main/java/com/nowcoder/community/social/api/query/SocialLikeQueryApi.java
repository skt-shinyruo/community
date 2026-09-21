package com.nowcoder.community.social.api.query;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SocialLikeQueryApi {

    boolean isLiked(UUID actorUserId, int entityType, UUID entityId);

    long count(int entityType, UUID entityId);

    Map<UUID, Long> counts(int entityType, List<UUID> entityIds);

    long userLikeCount(UUID userId);
}
