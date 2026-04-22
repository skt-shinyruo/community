package com.nowcoder.community.content.like;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SocialLikeQueryService implements LikeQueryService {

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final SocialLikeQueryApi likeQueryApi;

    public SocialLikeQueryService(SocialLikeQueryApi likeQueryApi) {
        this.likeQueryApi = likeQueryApi;
    }

    @Override
    public long countPostLikes(UUID postId) {
        if (postId == null) {
            return 0L;
        }
        return likeQueryApi.count(ENTITY_TYPE_POST, postId);
    }

    @Override
    public boolean hasLikedPost(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            return false;
        }
        return likeQueryApi.isLiked(userId, ENTITY_TYPE_POST, postId);
    }
}
