// 更新评论请求：作者在窗口内编辑评论内容。
package com.nowcoder.community.content.api.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateCommentRequest {

    @NotBlank
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

