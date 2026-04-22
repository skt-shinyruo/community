package com.nowcoder.community.content.entity;

import java.util.UUID;

public class PostTagName {

    private UUID postId;
    private String name;

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
