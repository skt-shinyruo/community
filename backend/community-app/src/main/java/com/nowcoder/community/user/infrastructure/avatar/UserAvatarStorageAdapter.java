package com.nowcoder.community.user.infrastructure.avatar;

import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarFileResult;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Component
public class UserAvatarStorageAdapter implements AvatarStoragePort {

    private final AvatarService avatarService;
    private final AvatarStorageRouter avatarStorageRouter;

    public UserAvatarStorageAdapter(AvatarService avatarService, AvatarStorageRouter avatarStorageRouter) {
        this.avatarService = avatarService;
        this.avatarStorageRouter = avatarStorageRouter;
    }

    @Override
    public AvatarUploadTokenResult createUploadToken(UUID userId) {
        return avatarService.createUploadToken(userId);
    }

    @Override
    public void upload(UUID userId, String fileName, MultipartFile file) {
        avatarService.upload(userId, fileName, file);
    }

    @Override
    public void assertAndConsumeUploadTicket(UUID userId, String fileName) {
        avatarService.assertAndConsumeUploadTicket(userId, fileName);
    }

    @Override
    public String buildAvatarUrl(String fileName) {
        return avatarService.buildAvatarUrl(fileName);
    }

    @Override
    public AvatarFileResult loadAvatarOrNull(String fileKey) {
        StoredAvatar stored = avatarStorageRouter.currentProviderOrThrow().loadOrNull(fileKey);
        return stored == null ? null : new AvatarFileResult(stored.resource(), stored.mediaType());
    }

}
