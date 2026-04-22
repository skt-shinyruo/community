package com.nowcoder.community.content.like;

import java.util.UUID;

public interface LikeQueryService {

    long countPostLikes(UUID postId);

    boolean hasLikedPost(UUID userId, UUID postId);
}
