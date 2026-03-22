package com.nowcoder.community.search.dto;

import java.time.Instant;
import java.util.List;

public class SearchPostItem {

    private int postId;
    private int userId;
    private Integer categoryId;
    private List<String> tags;
    private String title;
    private String highlightedTitle;
    private String highlightedContent;
    private Instant createTime;
    private Double score;

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHighlightedTitle() {
        return highlightedTitle;
    }

    public void setHighlightedTitle(String highlightedTitle) {
        this.highlightedTitle = highlightedTitle;
    }

    public String getHighlightedContent() {
        return highlightedContent;
    }

    public void setHighlightedContent(String highlightedContent) {
        this.highlightedContent = highlightedContent;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
