package com.nowcoder.community.content.dto;

import com.nowcoder.community.content.entity.Comment;

import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

public class UserRecentCommentResponse {

    private UUID id;
    private UUID userId;
    private int entityType;
    private UUID entityId;
    private UUID targetId;
    private UUID postId;
    private String postTitle;
    private String content;
    private Date createTime;

    public static UserRecentCommentResponse from(Comment comment, UUID postId, String postTitle) {
        return from(comment, postId, postTitle, null);
    }

    public static UserRecentCommentResponse from(Comment comment, UUID postId, String postTitle, Function<String, String> contentDecoder) {
        if (comment == null) {
            return null;
        }
        UserRecentCommentResponse response = new UserRecentCommentResponse();
        response.setId(comment.getId());
        response.setUserId(comment.getUserId());
        response.setEntityType(comment.getEntityType());
        response.setEntityId(comment.getEntityId());
        response.setTargetId(comment.getTargetId());
        response.setPostId(postId);
        response.setPostTitle(postTitle);
        String raw = comment.getContent();
        response.setContent(contentDecoder == null ? raw : contentDecoder.apply(raw));
        response.setCreateTime(comment.getCreateTime());
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public String getPostTitle() {
        return postTitle;
    }

    public void setPostTitle(String postTitle) {
        this.postTitle = postTitle;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
