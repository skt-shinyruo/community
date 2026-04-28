package com.nowcoder.community.user.application.result;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.util.Objects;

public record AvatarFileResult(Resource resource, MediaType mediaType) {

    public AvatarFileResult {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(mediaType, "mediaType");
    }
}
