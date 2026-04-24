package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.AvatarFileResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class UserFileApplicationService {

    private static final Pattern AVATAR_KEY_PATTERN = Pattern.compile(
            "^avatar/(?:\\d+|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/[0-9a-fA-F]{32}$"
    );

    private final AvatarStorageRouter router;

    public UserFileApplicationService(AvatarStorageRouter router) {
        this.router = router;
    }

    public AvatarFileResource loadAvatarOrNull(String requestUri) {
        String key = resolveKey(requestUri);
        if (!StringUtils.hasText(key) || !AVATAR_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        StoredAvatar stored = router.currentProviderOrThrow().loadOrNull(key);
        return stored == null ? null : new AvatarFileResource(stored.resource(), stored.mediaType());
    }

    private String resolveKey(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String prefix = "/files/";
        int idx = uri.indexOf(prefix);
        if (idx < 0) {
            return "";
        }
        String raw = uri.substring(idx + prefix.length());
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
