package com.nowcoder.community.content.dto;

import java.util.UUID;

public class CreatePostResponse {

    private UUID postId;

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }
}
