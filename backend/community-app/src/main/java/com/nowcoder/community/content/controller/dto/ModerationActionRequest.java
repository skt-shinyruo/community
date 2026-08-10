// 审核处置请求：版主/管理员对举报执行处置动作（reject/hide/delete/warn/mute/ban）。
package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ModerationActionRequest(
        @NotNull UUID reportId,
        @NotBlank String action,
        @NotBlank String reason,
        Integer durationSeconds
) {
}
