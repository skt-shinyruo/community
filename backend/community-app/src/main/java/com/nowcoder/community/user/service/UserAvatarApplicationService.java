package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.AvatarUploadTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;

@Service
public class UserAvatarApplicationService {

    private final AvatarService avatarService;
    private final UserService userService;

    public UserAvatarApplicationService(AvatarService avatarService, UserService userService) {
        this.avatarService = avatarService;
        this.userService = userService;
    }

    public AvatarUploadTokenResponse createUploadToken(UUID actorUserId, UUID userId) {
        requireSelf(actorUserId, userId);
        return avatarService.createUploadToken(userId);
    }

    public void upload(UUID actorUserId, UUID userId, String fileName, MultipartFile file) {
        requireSelf(actorUserId, userId);
        avatarService.upload(userId, fileName, file);
    }

    public void updateAvatar(UUID actorUserId, UUID userId, String fileName) {
        requireSelf(actorUserId, userId);
        avatarService.assertAndConsumeUploadTicket(userId, fileName);
        String url = avatarService.buildAvatarUrl(fileName);
        userService.updateHeaderUrl(userId, url);
    }

    private void requireSelf(UUID actorUserId, UUID userId) {
        if (actorUserId == null || !actorUserId.equals(userId)) {
            throw new BusinessException(FORBIDDEN, "只能操作自己的头像");
        }
    }
}
