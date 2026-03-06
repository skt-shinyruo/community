package com.nowcoder.community.content.like;

import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.social.application.SocialReadApplicationService;
import org.springframework.stereotype.Component;

@Component
public class SocialLikeQueryService implements LikeQueryService {

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final SocialReadApplicationService socialReadApplicationService;

    public SocialLikeQueryService(SocialReadApplicationService socialReadApplicationService) {
        this.socialReadApplicationService = socialReadApplicationService;
    }

    @Override
    public long countPostLikes(int postId) {
        return socialReadApplicationService.entityLikeCount(ENTITY_TYPE_POST, postId);
    }

    @Override
    public boolean hasLikedPost(int userId, int postId) {
        return socialReadApplicationService.hasLiked(userId, ENTITY_TYPE_POST, postId);
    }
}
