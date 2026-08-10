package com.nowcoder.community.user.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateUserRoleRequest(
        UUID targetUserId,
        @Min(value = 0, message = "type 非法") @Max(value = 2, message = "type 非法") int type,
        @NotBlank(message = "reason 不能为空") @Size(max = 200, message = "reason 过长（max=200）") String reason,
        boolean confirm
) {
}
