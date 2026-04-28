package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.social.application.LikeApplicationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public final class SocialLikeQueryApiAdapter implements SocialLikeQueryApi {

    private final LikeApplicationService delegate;

    public SocialLikeQueryApiAdapter(LikeApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isLiked(UUID actorUserId, int entityType, UUID entityId) {
        return delegate.isLiked(actorUserId, entityType, entityId);
    }

    @Override
    public long count(int entityType, UUID entityId) {
        return delegate.count(entityType, entityId);
    }

    @Override
    public long userLikeCount(UUID userId) {
        return delegate.userLikeCount(userId);
    }
}
