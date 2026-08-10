package com.nowcoder.community.ops.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record HotCacheDegradationRequest(boolean degraded, @NotBlank String reason) {
}
