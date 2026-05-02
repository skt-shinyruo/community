package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import com.nowcoder.community.social.application.LikeApplicationService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SocialLikeCleanupActionApiAdapter implements SocialLikeCleanupActionApi {

    private final LikeApplicationService likeApplicationService;

    public SocialLikeCleanupActionApiAdapter(LikeApplicationService likeApplicationService) {
        this.likeApplicationService = likeApplicationService;
    }

    @Override
    public long cleanupEntityLikes(int entityType, UUID entityId) {
        return likeApplicationService.cleanupEntityLikes(entityType, entityId);
    }
}
