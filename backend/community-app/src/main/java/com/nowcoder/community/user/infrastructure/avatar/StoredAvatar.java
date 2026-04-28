package com.nowcoder.community.user.infrastructure.avatar;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.util.Objects;

public record StoredAvatar(Resource resource, MediaType mediaType) {

    public StoredAvatar {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(mediaType, "mediaType");
    }
}
