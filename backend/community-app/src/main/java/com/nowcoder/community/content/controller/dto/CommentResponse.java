package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.CommentResult;

import java.util.Date;
import java.util.UUID;

/**
 * 评论/回复公共响应 DTO（字段白名单）。
 *
 * <p>说明：避免直接返回 entity 导致治理字段（status/deleted* 等）对外泄露。</p>
 */
public class CommentResponse {

    private UUID id;
    private UUID userId;
    private UUID postId;
    private UUID rootCommentId;
    private UUID parentCommentId;
    private UUID replyToUserId;
    private String content;
    private Date createTime;
    private Date updateTime;
    private int editCount;

    public static CommentResponse from(CommentResult view) {
        if (view == null) {
            return null;
        }
        CommentResponse response = new CommentResponse();
        response.id = view.id();
        response.userId = view.userId();
        response.postId = view.postId();
        response.rootCommentId = view.rootCommentId();
        response.parentCommentId = view.parentCommentId();
        response.replyToUserId = view.replyToUserId();
        response.content = view.content();
        response.createTime = view.createTime();
        response.updateTime = view.updateTime();
        response.editCount = view.editCount();
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

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public UUID getRootCommentId() {
        return rootCommentId;
    }

    public void setRootCommentId(UUID rootCommentId) {
        this.rootCommentId = rootCommentId;
    }

    public UUID getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(UUID parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public UUID getReplyToUserId() {
        return replyToUserId;
    }

    public void setReplyToUserId(UUID replyToUserId) {
        this.replyToUserId = replyToUserId;
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

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public int getEditCount() {
        return editCount;
    }

    public void setEditCount(int editCount) {
        this.editCount = editCount;
    }
}
