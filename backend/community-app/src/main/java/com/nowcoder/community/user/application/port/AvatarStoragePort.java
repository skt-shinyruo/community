package com.nowcoder.community.user.application.port;

import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;

import java.util.UUID;

public interface AvatarStoragePort {

    AvatarUploadSessionResult createUploadSession(UUID userId, CreateAvatarUploadSessionCommand command);

    String resolvePublicAvatarUrl(UUID userId, UUID objectId);
}
