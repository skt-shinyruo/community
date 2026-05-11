package com.nowcoder.community.user.application.command;

public record CreateAvatarUploadSessionCommand(
        String fileName,
        String contentType,
        long contentLength,
        String checksumSha256
) {

    public CreateAvatarUploadSessionCommand {
        fileName = normalize(fileName);
        contentType = normalize(contentType).toLowerCase();
        checksumSha256 = normalize(checksumSha256);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
