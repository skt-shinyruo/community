package com.nowcoder.community.content.like;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.like.LikeService;
import org.springframework.stereotype.Component;

@Component
public class SocialLikeQueryService implements LikeQueryService {

    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final LikeService likeService;

    public SocialLikeQueryService(LikeService likeService) {
        this.likeService = likeService;
    }

    @Override
    public long countPostLikes(int postId) {
        if (postId <= 0) {
            return 0L;
        }
        return likeService.count(ENTITY_TYPE_POST, postId);
    }

    @Override
    public boolean hasLikedPost(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            return false;
        }
        return likeService.isLiked(userId, ENTITY_TYPE_POST, postId);
    }
}
