package com.nowcoder.community.content.like;

public interface LikeQueryService {

    long countPostLikes(int postId);

    boolean hasLikedPost(int userId, int postId);
}

