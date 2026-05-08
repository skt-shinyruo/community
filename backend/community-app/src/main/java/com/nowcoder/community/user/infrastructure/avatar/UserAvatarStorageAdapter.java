package com.nowcoder.community.user.infrastructure.avatar;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarFileResult;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
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
    public void upload(UUID userId, String fileName, AvatarUploadContent content) {
        avatarService.upload(userId, fileName, content);
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
        if (stored == null) {
            return null;
        }
        Resource resource = stored.resource();
        try {
            long contentLength = contentLengthOrUnknown(resource);
            return new AvatarFileResult(
                    resource.getInputStream(),
                    stored.mediaType().toString(),
                    contentLength
            );
        } catch (IOException e) {
            throw new BusinessException(INTERNAL_ERROR, "读取头像失败", e);
        }
    }

    private long contentLengthOrUnknown(Resource resource) throws IOException {
        if (resource.isOpen()) {
            return -1;
        }
        return resource.contentLength();
    }

}
