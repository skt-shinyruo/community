package com.nowcoder.community.social.api.query;

import com.nowcoder.community.social.api.model.SocialBlockRelationView;

import java.util.List;
import java.util.UUID;

public interface SocialBlockQueryApi {

    boolean hasBlocked(UUID userId, UUID targetUserId);

    boolean isEitherBlocked(UUID userIdA, UUID userIdB);

    List<SocialBlockRelationView> scanBlockRelationsAfter(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit);

    default long currentBlockProjectionVersion() {
        return 0L;
    }
}
