package com.nowcoder.community.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

import java.util.List;

public class CreatePostRequest {

    @NotBlank
    @Size(max = ValidationLimits.POST_TITLE_MAX)
    private String title;

    @NotBlank
    @Size(max = ValidationLimits.POST_CONTENT_MAX)
    private String content;

    // 可选：分类（Discourse-like taxonomy）。
    private Integer categoryId;

    // 可选：标签（由服务端做归一化与数量限制）。
    @Size(max = ValidationLimits.TAGS_MAX)
    private List<@Size(max = ValidationLimits.TAG_MAX) String> tags;

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
