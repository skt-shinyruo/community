package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PrepareDriveUploadRequest(
        String parentId,
        @NotBlank String fileName,
        String contentType,
        @NotNull @Min(0) Long contentLength,
        String checksumSha256
) {
}
