package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.PostCreateResult;

import java.util.UUID;

public class CreatePostResponse {

    private UUID postId;

    public static CreatePostResponse from(PostCreateResult result) {
        CreatePostResponse response = new CreatePostResponse();
        response.setPostId(result == null ? null : result.postId());
        return response;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }
}
