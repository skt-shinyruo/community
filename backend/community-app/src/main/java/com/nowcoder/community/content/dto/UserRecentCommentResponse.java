package com.nowcoder.community.content.dto;

import com.nowcoder.community.content.entity.Comment;

import java.util.Date;
import java.util.function.Function;

public class UserRecentCommentResponse {

    private int id;
    private int userId;
    private int entityType;
    private int entityId;
    private int targetId;
    private int postId;
    private String postTitle;
    private String content;
    private Date createTime;

    public static UserRecentCommentResponse from(Comment comment, int postId, String postTitle) {
        return from(comment, postId, postTitle, null);
    }

    public static UserRecentCommentResponse from(Comment comment, int postId, String postTitle, Function<String, String> contentDecoder) {
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
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
