// 更新评论请求：作者在窗口内编辑评论内容。
package com.nowcoder.community.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.platform.validation.ValidationLimits;

public class UpdateCommentRequest {

    @NotBlank
    @Size(max = ValidationLimits.COMMENT_CONTENT_MAX)
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
