package com.nowcoder.community.content.application.port;

import java.util.UUID;

public interface LikeQueryPort {

    long countPostLikes(UUID postId);

    boolean hasLikedPost(UUID userId, UUID postId);
}
