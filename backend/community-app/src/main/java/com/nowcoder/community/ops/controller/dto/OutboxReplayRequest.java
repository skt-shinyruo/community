package com.nowcoder.community.ops.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record OutboxReplayRequest(@NotBlank String reason) {
}
