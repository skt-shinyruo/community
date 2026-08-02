package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public class DiscussPost {

    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_WONDERFUL = 1;
    public static final int STATUS_DELETED = 2;

    private UUID id;
    private UUID userId;
    private UUID categoryId;
    private String title;
    private int type;
    private int status;
    private Date createTime;
    private Date updateTime;
    private int editCount;
    private UUID deletedBy;
    private String deletedReason;
    private Date deletedTime;
    private int commentCount;
    private double score;
    private long scoreVersion;
    private long aggregateVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isDeleted() {
        return isDeletedStatus(status);
    }

    public boolean isActive() {
        return !isDeleted();
    }

    public boolean isWonderful() {
        return status == STATUS_WONDERFUL;
    }

    public static boolean isDeletedStatus(int status) {
        return status == STATUS_DELETED;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
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

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getScoreVersion() {
        return scoreVersion;
    }

    public void setScoreVersion(long scoreVersion) {
        this.scoreVersion = scoreVersion;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public void setAggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }
}
