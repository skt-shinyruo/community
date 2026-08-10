// 创建举报请求：支持对帖子/评论/用户提交举报（reason 必填，detail 可选）。
package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReportRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        @NotBlank String reason,
        String detail
) {
}
