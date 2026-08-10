package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

import java.util.UUID;

public record CreateCommentRequest(
        @NotBlank @Size(max = ValidationLimits.COMMENT_CONTENT_MAX) String content,
        UUID parentCommentId
) {
}
