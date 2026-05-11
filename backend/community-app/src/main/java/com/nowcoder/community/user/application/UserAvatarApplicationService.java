package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;

@Service
public class UserAvatarApplicationService {

    private final AvatarStoragePort avatarStoragePort;
    private final UserRepository userRepository;

    public UserAvatarApplicationService(AvatarStoragePort avatarStoragePort, UserRepository userRepository) {
        this.avatarStoragePort = avatarStoragePort;
        this.userRepository = userRepository;
    }

    public AvatarUploadSessionResult createUploadSession(UUID actorUserId, UUID userId, CreateAvatarUploadSessionCommand command) {
        requireSelf(actorUserId, userId);
        return avatarStoragePort.createUploadSession(userId, command);
    }

    @Transactional
    public void updateAvatar(UUID actorUserId, UUID userId, UUID objectId) {
        requireSelf(actorUserId, userId);
        String headerUrl = avatarStoragePort.resolvePublicAvatarUrl(userId, objectId);
        userRepository.updateHeaderUrl(userId, headerUrl);
    }

    private void requireSelf(UUID actorUserId, UUID userId) {
        if (actorUserId == null || !actorUserId.equals(userId)) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
    }
}
