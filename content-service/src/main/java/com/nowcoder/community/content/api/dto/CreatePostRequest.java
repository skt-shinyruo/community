package com.nowcoder.community.content.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class CreatePostRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    // 可选：分类（Discourse-like taxonomy）。
    private Integer categoryId;

    // 可选：标签（由服务端做归一化与数量限制）。
    private List<String> tags;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}
