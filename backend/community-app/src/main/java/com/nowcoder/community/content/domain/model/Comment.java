package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public class Comment {

    private static final int STATUS_ACTIVE = 0;

    private UUID id;
    private UUID postId;
    private UUID userId;
    private UUID rootCommentId;
    private UUID parentCommentId;
    private UUID replyToUserId;
    private String content;
    private int status;
    private Date createTime;
    private Date updateTime;
    private int editCount;
    private UUID deletedBy;
    private String deletedReason;
    private Date deletedTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isActive() {
        return status == STATUS_ACTIVE;
    }

    public boolean isRootComment() {
        return parentCommentId == null;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getDeletedReason() {
        return deletedReason;
    }

    public void setDeletedReason(String deletedReason) {
        this.deletedReason = deletedReason;
    }

    public Date getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(Date deletedTime) {
        this.deletedTime = deletedTime;
    }
}
