package com.nowcoder.community.user.application.result;

public record AvatarUploadTokenResult(
        String provider,
        String uploadToken,
        String fileName,
        String bucketUrl,
        String uploadUrl,
        String uploadMethod,
        long maxBytes,
        String mimeLimit
) {
}
