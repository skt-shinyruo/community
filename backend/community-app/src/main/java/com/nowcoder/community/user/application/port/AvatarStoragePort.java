package com.nowcoder.community.user.application.port;

import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;

import java.util.UUID;

public interface AvatarStoragePort {

    AvatarUploadTokenResult createUploadToken(UUID userId);

    void upload(UUID userId, String fileKey, AvatarUploadContent content);

    void assertAndConsumeUploadTicket(UUID userId, String fileKey);

    String buildAvatarUrl(String fileKey);
}
