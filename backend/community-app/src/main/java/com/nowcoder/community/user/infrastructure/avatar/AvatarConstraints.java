package com.nowcoder.community.user.infrastructure.avatar;

import java.util.List;

public final class AvatarConstraints {

    public static final String KEY_PREFIX = "avatar/";
    public static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    public static final String MIME_LIMIT = "image/jpeg;image/png;image/webp;image/gif";
    public static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private AvatarConstraints() {
    }
}
