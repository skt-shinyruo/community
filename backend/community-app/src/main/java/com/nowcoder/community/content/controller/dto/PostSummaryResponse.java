package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.PostSummaryResult;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class PostSummaryResponse {

    private UUID id;
    private UUID userId;
    private String title;
    private String preview;
    private int type;
    private int status;
    private Date createTime;
    private int commentCount;
    private double score;

    // taxonomy：分类与标签（可选字段）
    private UUID categoryId;
    private List<String> tags;

    // Discourse-like: 列表所需的“最后回复/活动”信息（可选字段）
    private UUID lastReplyUserId;
    private Date lastReplyTime;
    private Date lastActivityTime;
    private String lastReplyPreview;

    public static PostSummaryResponse from(PostSummaryResult view) {
        if (view == null) {
            return null;
        }
        PostSummaryResponse response = new PostSummaryResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setPreview(view.preview());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLastReplyUserId(view.lastReplyUserId());
        response.setLastReplyTime(view.lastReplyTime());
        response.setLastActivityTime(view.lastActivityTime());
        response.setLastReplyPreview(view.lastReplyPreview());
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
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

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public UUID getLastReplyUserId() {
        return lastReplyUserId;
    }

    public void setLastReplyUserId(UUID lastReplyUserId) {
        this.lastReplyUserId = lastReplyUserId;
    }

    public Date getLastReplyTime() {
        return lastReplyTime;
    }

    public void setLastReplyTime(Date lastReplyTime) {
        this.lastReplyTime = lastReplyTime;
    }

    public Date getLastActivityTime() {
        return lastActivityTime;
    }

    public void setLastActivityTime(Date lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public String getLastReplyPreview() {
        return lastReplyPreview;
    }

    public void setLastReplyPreview(String lastReplyPreview) {
        this.lastReplyPreview = lastReplyPreview;
    }
}
