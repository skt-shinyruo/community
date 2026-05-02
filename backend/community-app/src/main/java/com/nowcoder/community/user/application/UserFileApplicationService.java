package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.AvatarStoragePort;
import com.nowcoder.community.user.application.result.AvatarFileResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class UserFileApplicationService {

    private static final Pattern AVATAR_KEY_PATTERN = Pattern.compile(
            "^avatar/(?:\\d+|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/[0-9a-fA-F]{32}$"
    );

    private final AvatarStoragePort avatarStoragePort;

    public UserFileApplicationService(AvatarStoragePort avatarStoragePort) {
        this.avatarStoragePort = avatarStoragePort;
    }

    public AvatarFileResult loadAvatarOrNull(String fileKey) {
        String key = fileKey == null ? "" : fileKey.trim();
        if (!StringUtils.hasText(key) || !AVATAR_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        return avatarStoragePort.loadAvatarOrNull(key);
    }
}
