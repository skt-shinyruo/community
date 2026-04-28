package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.application.FollowApplicationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public final class SocialFollowQueryApiAdapter implements SocialFollowQueryApi {

    private final FollowApplicationService delegate;

    public SocialFollowQueryApiAdapter(FollowApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId) {
        return delegate.hasFollowed(actorUserId, entityType, entityId);
    }

    @Override
    public long followeeCount(UUID userId, int entityType) {
        return delegate.followeeCount(userId, entityType);
    }

    @Override
    public long followerCount(int entityType, UUID entityId) {
        return delegate.followerCount(entityType, entityId);
    }
}
