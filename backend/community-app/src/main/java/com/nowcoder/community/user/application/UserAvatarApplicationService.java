package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Objects;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;

@Service
public class UserAvatarApplicationService {

    private final AvatarStoragePort avatarStoragePort;
    private final UserAvatarTransactionOperations transactionOperations;

    public UserAvatarApplicationService(
            AvatarStoragePort avatarStoragePort,
            UserAvatarTransactionOperations transactionOperations
    ) {
        this.avatarStoragePort = Objects.requireNonNull(avatarStoragePort, "avatarStoragePort must not be null");
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations,
                "transactionOperations must not be null"
        );
    }

    public AvatarUploadSessionResult createUploadSession(UUID actorUserId, UUID userId, CreateAvatarUploadSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireSelf(actorUserId, userId);
        return avatarStoragePort.createUploadSession(userId, command);
    }

    public void updateAvatar(UUID actorUserId, UUID userId, UUID objectId) {
        requireSelf(actorUserId, userId);
        String headerUrl = avatarStoragePort.resolvePublicAvatarUrl(userId, objectId);
        transactionOperations.updateHeaderUrl(userId, headerUrl);
    }

    private void requireSelf(UUID actorUserId, UUID userId) {
        if (actorUserId == null || !actorUserId.equals(userId)) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
    }
}
