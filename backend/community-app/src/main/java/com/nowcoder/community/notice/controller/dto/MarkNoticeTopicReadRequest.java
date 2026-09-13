package com.nowcoder.community.notice.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record MarkNoticeTopicReadRequest(
        @NotBlank
        String topic
) {
}
