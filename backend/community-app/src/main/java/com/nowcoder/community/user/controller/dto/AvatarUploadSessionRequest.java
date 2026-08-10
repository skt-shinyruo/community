package com.nowcoder.community.user.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AvatarUploadSessionRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Positive long contentLength,
        String checksumSha256
) {
}
