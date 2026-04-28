package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.social.application.BlockApplicationService;
import com.nowcoder.community.social.application.result.BlockRelationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public final class SocialBlockQueryApiAdapter implements SocialBlockQueryApi {

    private final BlockApplicationService delegate;

    public SocialBlockQueryApiAdapter(BlockApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        return delegate.hasBlocked(userId, targetUserId);
    }

    @Override
    public boolean isEitherBlocked(UUID userIdA, UUID userIdB) {
        return delegate.isEitherBlocked(userIdA, userIdB);
    }

    @Override
    public List<SocialBlockRelationView> scanBlockRelationsAfter(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        return delegate.scanBlockRelationsAfter(afterBlockerUserId, afterBlockedUserId, limit)
                .stream()
                .map(this::toView)
                .toList();
    }

    private SocialBlockRelationView toView(BlockRelationResult result) {
        return new SocialBlockRelationView(result.blockerUserId(), result.blockedUserId());
    }
}
