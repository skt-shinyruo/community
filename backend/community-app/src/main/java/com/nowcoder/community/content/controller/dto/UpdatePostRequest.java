// 更新帖子请求：作者在窗口内编辑帖子（标题/正文/分类/标签）。
package com.nowcoder.community.content.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

import java.util.List;
import java.util.UUID;

public class UpdatePostRequest {

    @NotBlank
    @Size(max = ValidationLimits.POST_TITLE_MAX)
    private String title;

    @Valid
    @NotEmpty
    @Size(max = ValidationLimits.POST_CONTENT_BLOCKS_MAX)
    private List<PostContentBlockRequest> blocks;

    private UUID categoryId;

    @Size(max = ValidationLimits.TAGS_MAX)
    private List<@Size(max = ValidationLimits.TAG_MAX) String> tags;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<PostContentBlockRequest> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<PostContentBlockRequest> blocks) {
        this.blocks = blocks;
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
}
