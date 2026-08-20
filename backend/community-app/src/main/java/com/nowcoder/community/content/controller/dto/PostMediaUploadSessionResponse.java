package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PostMediaUploadSessionResponse(
        UUID assetId,
        String uploadId,
        UploadInstruction upload,
        Constraints constraints,
        String expiresAt
) {

    public static PostMediaUploadSessionResponse from(PostMediaUploadSessionResult result) {
        if (result == null) {
            return null;
        }
        return new PostMediaUploadSessionResponse(
                result.assetId(),
                result.uploadId(),
                new UploadInstruction(
                        result.uploadUrl(),
                        result.uploadMethod(),
                        result.fileField(),
                        Map.of(result.uploadIdField(), result.uploadId()),
                        Map.of()
                ),
                new Constraints(result.maxBytes(), parseMimeTypes(result.mimeTypes())),
                result.expiresAt() == null ? "" : result.expiresAt().toString()
        );
    }

    private static List<String> parseMimeTypes(String mimeTypes) {
        if (mimeTypes == null || mimeTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(mimeTypes.split(";"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public record UploadInstruction(
            String url,
            String method,
            String fileField,
            Map<String, String> fields,
            Map<String, String> headers
    ) {
    }

    public record Constraints(long maxBytes, List<String> mimeTypes) {
    }
}
