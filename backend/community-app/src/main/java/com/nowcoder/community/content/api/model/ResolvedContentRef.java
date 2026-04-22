package com.nowcoder.community.content.api.model;

import java.util.UUID;

public record ResolvedContentRef(UUID entityUserId, UUID postId) {
}
