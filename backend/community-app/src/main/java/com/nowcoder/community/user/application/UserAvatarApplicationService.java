package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import com.nowcoder.community.user.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    public AvatarUploadTokenResult createUploadToken(UUID actorUserId, UUID userId) {
        requireSelf(actorUserId, userId);
        return avatarStoragePort.createUploadToken(userId);
    }

    public void upload(UUID actorUserId, UUID userId, String fileName, MultipartFile file) {
        requireSelf(actorUserId, userId);
        avatarStoragePort.upload(userId, fileName, file);
    }

    @Transactional
    public void updateAvatar(UUID actorUserId, UUID userId, String fileName) {
        requireSelf(actorUserId, userId);
        avatarStoragePort.assertAndConsumeUploadTicket(userId, fileName);
        String headerUrl = avatarStoragePort.buildAvatarUrl(fileName);
        userRepository.updateHeaderUrl(userId, headerUrl);
    }

    private void requireSelf(UUID actorUserId, UUID userId) {
        if (actorUserId == null || !actorUserId.equals(userId)) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
    }
}
