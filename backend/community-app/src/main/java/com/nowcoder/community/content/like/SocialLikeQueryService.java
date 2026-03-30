package com.nowcoder.community.content.like;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.springframework.stereotype.Component;

@Component
public class SocialLikeQueryService implements LikeQueryService {

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final SocialLikeQueryApi likeQueryApi;

    public SocialLikeQueryService(SocialLikeQueryApi likeQueryApi) {
        this.likeQueryApi = likeQueryApi;
    }

    @Override
    public long countPostLikes(int postId) {
        if (postId <= 0) {
            return 0L;
        }
        return likeQueryApi.count(ENTITY_TYPE_POST, postId);
    }

    @Override
    public boolean hasLikedPost(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            return false;
        }
        return likeQueryApi.isLiked(userId, ENTITY_TYPE_POST, postId);
    }
}
