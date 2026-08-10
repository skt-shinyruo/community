package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PreparePostMediaUploadRequest(
        UUID requestId,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Positive long contentLength,
        String mediaKind,
        String checksumSha256
) {
}
