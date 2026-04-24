package com.nowcoder.community.user.dto;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record AvatarFileResource(Resource resource, MediaType mediaType) {
}
