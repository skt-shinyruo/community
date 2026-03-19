package com.nowcoder.community.content.dto;

import java.util.Date;
import java.util.List;

public class PostSummaryResponse {

    private int id;
    private int userId;
    private String title;
    private int type;
    private int status;
    private Date createTime;
    private int commentCount;
    private double score;

    // taxonomy：分类与标签（可选字段）
    private Integer categoryId;
    private List<String> tags;

    // Discourse-like: 列表所需的“最后回复/活动”信息（可选字段）
    private Integer lastReplyUserId;
    private Date lastReplyTime;
    private Date lastActivityTime;

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

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Integer getLastReplyUserId() {
        return lastReplyUserId;
    }

    public void setLastReplyUserId(Integer lastReplyUserId) {
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
}
