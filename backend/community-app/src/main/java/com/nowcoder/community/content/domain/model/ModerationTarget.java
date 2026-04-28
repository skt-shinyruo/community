package com.nowcoder.community.content.domain.model;

import java.util.UUID;

public record ModerationTarget(
        int targetType,
        UUID targetId,
        UUID targetUserId
) {
}
