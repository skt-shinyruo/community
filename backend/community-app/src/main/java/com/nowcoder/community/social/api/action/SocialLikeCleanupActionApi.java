package com.nowcoder.community.social.api.action;

import java.util.UUID;

public interface SocialLikeCleanupActionApi {

    long cleanupEntityLikes(int entityType, UUID entityId);
}
