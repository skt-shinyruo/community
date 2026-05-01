package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.application.LikeQueryPort;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SocialLikeQueryAdapter implements LikeQueryPort {

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final SocialLikeQueryApi likeQueryApi;

    public SocialLikeQueryAdapter(SocialLikeQueryApi likeQueryApi) {
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
