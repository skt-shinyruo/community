package com.nowcoder.community.content.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

import java.util.List;
import java.util.UUID;

public record UpdatePostRequest(
        @NotBlank @Size(max = ValidationLimits.POST_TITLE_MAX) String title,
        @Valid @NotEmpty @Size(max = ValidationLimits.POST_CONTENT_BLOCKS_MAX) List<PostContentBlockRequest> blocks,
        UUID categoryId,
        @Size(max = ValidationLimits.TAGS_MAX) List<@Size(max = ValidationLimits.TAG_MAX) String> tags
) {
}
