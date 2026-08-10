package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record PostContentBlockRequest(
        String type,
        @Size(max = ValidationLimits.POST_BLOCK_TEXT_MAX) String text,
        UUID assetId,
        @Size(max = ValidationLimits.POST_BLOCK_LANGUAGE_MAX) String language,
        @Size(max = ValidationLimits.POST_BLOCK_CAPTION_MAX) String caption,
        @Size(max = ValidationLimits.POST_BLOCK_DISPLAY_NAME_MAX) String displayName,
        Map<String, Object> metadata
) {
}
